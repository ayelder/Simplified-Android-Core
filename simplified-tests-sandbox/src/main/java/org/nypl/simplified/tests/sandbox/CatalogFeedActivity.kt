package org.nypl.simplified.tests.sandbox

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProviders
import com.google.common.util.concurrent.MoreExecutors
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import org.librarysimplified.services.api.ServiceDirectoryProviderType
import org.librarysimplified.services.api.ServiceDirectoryType
import org.nypl.simplified.accounts.api.AccountProviderImmutable
import org.nypl.simplified.books.api.Book
import org.nypl.simplified.books.api.BookFormat
import org.nypl.simplified.books.book_database.api.BookFormats
import org.nypl.simplified.books.book_registry.BookRegistry
import org.nypl.simplified.books.book_registry.BookRegistryReadableType
import org.nypl.simplified.books.book_registry.BookRegistryType
import org.nypl.simplified.books.bundled.api.BundledContentResolverType
import org.nypl.simplified.books.controller.api.BooksControllerType
import org.nypl.simplified.books.covers.BookCoverBadge
import org.nypl.simplified.books.covers.BookCoverBadgeLookupType
import org.nypl.simplified.books.covers.BookCoverGenerator
import org.nypl.simplified.books.covers.BookCoverProvider
import org.nypl.simplified.books.covers.BookCoverProviderType
import org.nypl.simplified.documents.store.DocumentStoreType
import org.nypl.simplified.feeds.api.FeedEntry
import org.nypl.simplified.feeds.api.FeedHTTPTransport
import org.nypl.simplified.feeds.api.FeedLoader
import org.nypl.simplified.feeds.api.FeedLoaderType
import org.nypl.simplified.http.core.HTTP
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import org.nypl.simplified.opds.core.OPDSFeedParser
import org.nypl.simplified.opds.core.OPDSSearchParser
import org.nypl.simplified.presentableerror.api.PresentableErrorType
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.tenprint.TenPrintGenerator
import org.nypl.simplified.tests.MockBooksController
import org.nypl.simplified.tests.MockDocumentStore
import org.nypl.simplified.tests.MockProfilesController
import org.nypl.simplified.tests.MutableServiceDirectory
import org.nypl.simplified.threads.NamedThreadPools
import org.nypl.simplified.toolbar.ToolbarHostType
import org.nypl.simplified.ui.catalog.CatalogConfigurationServiceType
import org.nypl.simplified.ui.catalog.CatalogFeedArguments
import org.nypl.simplified.ui.catalog.CatalogFragmentBookDetail
import org.nypl.simplified.ui.catalog.CatalogFragmentBookDetailParameters
import org.nypl.simplified.ui.catalog.CatalogFragmentFeed
import org.nypl.simplified.ui.catalog.CatalogNavigationControllerType
import org.nypl.simplified.ui.errorpage.ErrorPageFragment
import org.nypl.simplified.ui.errorpage.ErrorPageListenerType
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.host.HostViewModel
import org.nypl.simplified.ui.host.HostViewModelFactory
import org.nypl.simplified.ui.host.HostViewModelType
import org.nypl.simplified.ui.screen.ScreenSizeInformation
import org.nypl.simplified.ui.screen.ScreenSizeInformationType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.LoggerFactory
import java.net.URI

class CatalogFeedActivity : AppCompatActivity(), ToolbarHostType, ErrorPageListenerType {

  override fun toolbarBackArrow(): Drawable {
    return this.getDrawable(R.drawable.toolbar_back_arrow)
  }

  private val logger = LoggerFactory.getLogger(CatalogFeedActivity::class.java)

  companion object {
//    val targetURI = URI.create(
//      "https://circulation.librarysimplified.org/NYNYPL/feed/1?entrypoint=Book")

    val targetURI = URI.create(
      "https://circulation.librarysimplified.org/NYNYPL/")
  }

  class Services(
    private val context: Context
  ) : ServiceDirectoryProviderType {

    lateinit var profiles: MockProfilesController

    @Volatile
    private var services: ServiceDirectoryType? = null

    override fun serviceDirectory(): ServiceDirectoryType {
      return this.services
        ?: this.run {
          val newServices = this.createServices()
          this.services = newServices
          newServices
        }
    }

    private fun createServices(): ServiceDirectoryType {
      val newServices = MutableServiceDirectory()

      val executor =
        NamedThreadPools.namedThreadPool(4, "bg", 19)

      this.profiles =
        MockProfilesController(
          profileCount = 2,
          accountCount = 3
        )

      profiles.profileAccountCurrent()
        .setAccountProvider(
          AccountProviderImmutable.copy(profiles.profileAccountCurrent().provider)
            .copy(
              catalogURI = targetURI,
              displayName = "The New York Public Library",
              subtitle = "Inspiring lifelong learning, advancing knowledge, and strengthening our communities."
            )
        )

      val bookController =
        MockBooksController()

      val bookRegistry =
        BookRegistry.create()

      val documentStore =
        MockDocumentStore()

      val feedLoader =
        FeedLoader.create(
          exec = MoreExecutors.listeningDecorator(executor),
          parser = OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser(BookFormats.supportedBookMimeTypes())),
          searchParser = OPDSSearchParser.newParser(),
          transport = FeedHTTPTransport.newTransport(HTTP.newHTTP()),
          bookRegistry = bookRegistry,
          bundledContent = BundledContentResolverType { null }
        )

      val coverLoader =
        BookCoverProvider.newCoverProvider(
          context = this.context,
          bookRegistry = bookRegistry,
          coverGenerator = BookCoverGenerator(TenPrintGenerator.newGenerator()),
          badgeLookup = object : BookCoverBadgeLookupType {
            override fun badgeForEntry(entry: FeedEntry.FeedEntryOPDS): BookCoverBadge? {
              return null
            }
          },
          executor = executor,
          debugCacheIndicators = true,
          debugLogging = false
        )

      newServices.putService(
        interfaceType = DocumentStoreType::class.java,
        service = documentStore
      )
      newServices.putService(
        interfaceType = BookCoverProviderType::class.java,
        service = coverLoader
      )
      newServices.putService(
        interfaceType = FeedLoaderType::class.java,
        service = feedLoader
      )
      newServices.putService(
        interfaceType = CatalogConfigurationServiceType::class.java,
        service = object : CatalogConfigurationServiceType {
          override val supportErrorReportEmailAddress: String
            get() = "someone@example.com"
          override val supportErrorReportSubject: String
            get() = "[printer on fire]"
          override val showAllCollectionsInLocalFeeds: Boolean
            get() = true
        }
      )
      newServices.putService(
        interfaceType = ProfilesControllerType::class.java,
        service = profiles
      )
      newServices.putService(
        interfaceType = UIThreadServiceType::class.java,
        service = object : UIThreadServiceType {}
      )
      newServices.putService(
        interfaceType = BookRegistryType::class.java,
        service = bookRegistry
      )
      newServices.putService(
        interfaceType = BookRegistryReadableType::class.java,
        service = bookRegistry
      )
      newServices.putService(
        interfaceType = ScreenSizeInformationType::class.java,
        service = ScreenSizeInformation(this.context.resources)
      )
      newServices.putService(
        interfaceType = BooksControllerType::class.java,
        service = bookController
      )
      return newServices
    }
  }

  class CatalogNavigationController(
    private val services: Services,
    private val context: AppCompatActivity
  ) : CatalogNavigationControllerType {
    override fun backStackSize(): Int {
      return 0
    }

    override fun openEPUBReader(
      book: Book,
      format: BookFormat.BookFormatEPUB
    ) {

    }

    override fun openAudioBookListener(
      book: Book,
      format: BookFormat.BookFormatAudioBook
    ) {

    }

    override fun openPDFReader(
      book: Book,
      format: BookFormat.BookFormatPDF
    ) {

    }

    override fun openFeed(feedArguments: CatalogFeedArguments) {
      LeakCanary.config =
        LeakCanary.config.copy(
          dumpHeap = true
        )

      this.context.supportFragmentManager
        .beginTransaction()
        .setCustomAnimations(
          R.anim.slide_in_right,
          R.anim.slide_out_left,
          android.R.anim.slide_in_left,
          android.R.anim.slide_out_right
        )
        .addToBackStack(null)
        .replace(R.id.fragmentHolder, CatalogFragmentFeed.create(feedArguments))
        .commit()
    }

    override fun popBackStack() {
      LeakCanary.config =
        LeakCanary.config.copy(
          dumpHeap = true
        )

      this.context.supportFragmentManager.popBackStack()
    }

    override fun openBookDetail(entry: FeedEntry.FeedEntryOPDS) {
      LeakCanary.config =
        LeakCanary.config.copy(
          dumpHeap = true
        )

      val parameters =
        CatalogFragmentBookDetailParameters(
          accountId = services.profiles.profileAccountCurrent().id,
          feedEntry = entry
        )

      this.context.supportFragmentManager
        .beginTransaction()
        .setCustomAnimations(
          R.anim.slide_in_right,
          R.anim.slide_out_left,
          android.R.anim.slide_in_left,
          android.R.anim.slide_out_right
        )
        .addToBackStack(null)
        .replace(R.id.fragmentHolder, CatalogFragmentBookDetail.create(parameters))
        .commit()
    }

    override fun <E : PresentableErrorType> openErrorPage(parameters: ErrorPageParameters<E>) {
      this.context.supportFragmentManager
        .beginTransaction()
        .setCustomAnimations(
          R.anim.slide_in_right,
          R.anim.slide_out_left,
          android.R.anim.slide_in_left,
          android.R.anim.slide_out_right
        )
        .addToBackStack(null)
        .replace(R.id.fragmentHolder, ErrorPageFragment.create(parameters))
        .commit()
    }
  }

  private lateinit var model: HostViewModelType

  override fun onCreate(savedInstanceState: Bundle?) {

    LeakCanary.config =
      LeakCanary.config.copy(
        dumpHeap = true
      )

    AppWatcher.config =
      AppWatcher.config.copy(
        enabled = true,
        watchActivities = true,
        watchFragments = true,
        watchDurationMillis = 1_000L,
        watchFragmentViews = true
      )

    val services = Services(this.applicationContext)

    this.model =
      ViewModelProviders.of(this, HostViewModelFactory(services))
        .get(HostViewModel::class.java)

    this.model.updateNavigationController(
      navigationInterface = CatalogNavigationControllerType::class.java,
      navigationInstance = CatalogNavigationController(services, this)
    )

    super.onCreate(savedInstanceState)
    this.setContentView(R.layout.fragment_host)

    this.toolbar = this.findViewById(R.id.toolbar)
    this.toolbar.overflowIcon = this.getDrawable(org.nypl.simplified.ui.catalog.R.drawable.overflow)

    this.toolbar.menu.clear()
    this.toolbar.inflateMenu(R.menu.catalog)

    this.toolbar.title =
      services.profiles.profileAccountCurrent().provider.displayName
    this.toolbar.subtitle =
      services.profiles.profileAccountCurrent().provider.subtitle

    this.toolbar.setTitleTextAppearance(
      this, R.style.SimplifiedTitleTextAppearance)
    this.toolbar.setSubtitleTextAppearance(
      this, R.style.SimplifiedSubTitleTextAppearance)

    if (savedInstanceState == null) {
      val fragment =
        CatalogFragmentFeed.create(
          CatalogFeedArguments.CatalogFeedArgumentsRemote(
            title = "Catalog",
            feedURI = targetURI,
            isSearchResults = false,
            accountId = services.profiles.profileAccountCurrent().id
          )
        )

      this.supportFragmentManager.beginTransaction()
        .setCustomAnimations(
          R.anim.slide_in_right,
          R.anim.slide_out_left,
          android.R.anim.slide_in_left,
          android.R.anim.slide_out_right
        )
        .replace(R.id.fragmentHolder, fragment, "MAIN")
        .commit()
    }
  }

  override lateinit var toolbar: Toolbar

  override fun onStop() {
    super.onStop()

    this.model.removeNavigationController(CatalogNavigationControllerType::class.java)
  }

  override fun onErrorPageSendReport(parameters: ErrorPageParameters<*>) {

  }
}
