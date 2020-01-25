package org.nypl.simplified.books.controller

import com.io7m.jfunctional.Option

import org.nypl.simplified.accounts.api.AccountAuthenticatedHTTP
import org.nypl.simplified.accounts.api.AccountLoginState
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.books.api.BookIDs
import org.nypl.simplified.books.book_database.api.BookDatabaseException
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.book_registry.BookStatus
import org.nypl.simplified.books.book_registry.BookWithStatus
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.http.core.HTTPResultError
import org.nypl.simplified.http.core.HTTPResultException
import org.nypl.simplified.http.core.HTTPResultMatcherType
import org.nypl.simplified.http.core.HTTPResultOKType
import org.nypl.simplified.http.core.HTTPType
import org.nypl.simplified.opds.core.OPDSAvailabilityRevoked
import org.nypl.simplified.opds.core.OPDSFeedParserType
import org.nypl.simplified.opds.core.OPDSParseException
import org.slf4j.LoggerFactory

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.util.HashSet
import java.util.concurrent.Callable

class BookSyncTask(
  private val booksController: BooksControllerType,
  private val account: AccountType,
  private val bookRegistry: BookRegistryType,
  private val http: HTTPType,
  private val feedParser: OPDSFeedParserType
) : Callable<Unit> {

  private val logger = LoggerFactory.getLogger(BookSyncTask::class.java)

  @Throws(Exception::class)
  override fun call() {
    try {
      this.logger.debug("syncing account {}", this.account.id)
      return this.execute()
    } finally {
      this.logger.debug("finished syncing account {}", this.account.id)
    }
  }

  @Throws(Exception::class)
  private fun execute() {
    val provider = this.account.provider
    val providerAuth = provider.authentication

    if (providerAuth == null) {
      this.logger.debug("account does not support syncing")
      return
    }

    val credentials = this.account.loginState.credentials
    if (credentials == null) {
      this.logger.debug("no credentials, aborting!")
      return
    }

    val auth =
      AccountAuthenticatedHTTP.createAuthenticatedHTTP(credentials)
    val result =
      this.http.get(Option.some(auth), provider.loansURI, 0L)

    return result.matchResult(
      object : HTTPResultMatcherType<InputStream, Unit, Exception> {
        @Throws(Exception::class)
        override fun onHTTPError(e: HTTPResultError<InputStream>) {
          return this@BookSyncTask.onHTTPError(e, provider)
        }

        @Throws(Exception::class)
        override fun onHTTPException(e: HTTPResultException<InputStream>) {
          throw e.error
        }

        @Throws(Exception::class)
        override fun onHTTPOK(e: HTTPResultOKType<InputStream>) {
          return this@BookSyncTask.onHTTPOK(e, provider)
        }
      })
  }

  @Throws(IOException::class)
  private fun onHTTPOK(
    result: HTTPResultOKType<InputStream>,
    provider: AccountProviderType
  ) {
    return result.use { ok ->
      this.parseFeed(ok, provider)
      return
    }
  }

  @Throws(OPDSParseException::class)
  private fun parseFeed(
    result: HTTPResultOKType<InputStream>,
    provider: AccountProviderType
  ) {

    val feed = this.feedParser.parse(provider.loansURI, result.value)

    /*
     * Obtain the set of books that are on disk already. If any
     * of these books are not in the received feed, then they have
     * expired and should be deleted.
     */

    val bookDatabase = this.account.bookDatabase
    val existing = bookDatabase.books()

    /*
     * Handle each book in the received feed.
     */

    val received = HashSet<BookID>(64)
    val entries = feed.feedEntries
    for (opdsEntry in entries) {
      val bookId = BookIDs.newFromOPDSEntry(opdsEntry)
      received.add(bookId)
      this.logger.debug("[{}] updating", bookId.brief())

      try {
        val databaseEntry = bookDatabase.createOrUpdate(bookId, opdsEntry)
        val book = databaseEntry.book
        this.bookRegistry.update(BookWithStatus(book, BookStatus.fromBook(book)))
      } catch (e: BookDatabaseException) {
        this.logger.error("[{}] unable to update database entry: ", bookId.brief(), e)
      }
    }

    /*
     * Now delete any book that previously existed, but is not in the
     * received set. Queue any revoked books for completion and then
     * deletion.
     */

    val revoking = HashSet<BookID>(existing.size)
    for (existingId in existing) {
      try {
        this.logger.debug("[{}] checking for deletion", existingId.brief())

        if (!received.contains(existingId)) {
          val dbEntry = bookDatabase.entry(existingId)
          val a = dbEntry.book.entry.availability
          if (a is OPDSAvailabilityRevoked) {
            revoking.add(existingId)
          }

          this.logger.debug("[{}] deleting", existingId.brief())
          dbEntry.delete()
          this.bookRegistry.clearFor(existingId)
        } else {
          this.logger.debug("[{}] keeping", existingId.brief())
        }
      } catch (x: Throwable) {
        this.logger.error("[{}]: unable to delete entry: ", existingId.value(), x)
      }
    }

    /*
     * Finish the revocation of any books that need it.
     */

    for (revoke_id in revoking) {
      this.logger.debug("[{}] revoking", revoke_id.brief())
      this.booksController.bookRevoke(this.account, revoke_id)
    }
  }

  @Throws(Exception::class)
  private fun onHTTPError(
    result: HTTPResultError<InputStream>,
    provider: AccountProviderType
  ) {

    if (result.status == HttpURLConnection.HTTP_UNAUTHORIZED) {
      this.logger.debug("removing credentials due to 401 server response")
      this.account.setLoginState(AccountLoginState.AccountNotLoggedIn)
      return
    }

    throw IOException(String.format("%s: %d: %s", provider.loansURI, result.status, result.message))
  }
}