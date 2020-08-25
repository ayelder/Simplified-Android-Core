package org.nypl.simplified.tests.books.book_database

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nypl.drm.core.AdobeAdeptLoan
import org.nypl.drm.core.AdobeLoanID
import org.nypl.simplified.books.book_database.BookDRMInformationHandleACS
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_EPUB
import org.nypl.simplified.books.book_database.api.BookFormats.BookFormatDefinition.BOOK_FORMAT_PDF
import org.nypl.simplified.files.DirectoryUtilities
import org.nypl.simplified.files.FileUtilities
import org.nypl.simplified.tests.TestDirectories
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer

abstract class BookDRMInformationHandleACSContract {

  private val logger =
    LoggerFactory.getLogger(BookDRMInformationHandleACSContract::class.java)

  private lateinit var directory1: File
  private lateinit var directory0: File

  @Before
  fun testSetup() {
    this.directory0 = TestDirectories.temporaryDirectory()
    this.directory1 = TestDirectories.temporaryDirectory()
  }

  @After
  fun testTearDown() {
    DirectoryUtilities.directoryDelete(this.directory0)
    DirectoryUtilities.directoryDelete(this.directory1)
  }

  /**
   * Creating a handle from an empty directory yields an empty handle.
   *
   * @throws Exception On errors
   */

  @Test
  fun testEmpty() {
    val handle =
      BookDRMInformationHandleACS(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB
      )

    assertEquals(null, handle.info.acsmFile)
    assertEquals(null, handle.info.rights)
  }

  /**
   * Copying in an ACSM saves the ACSM.
   *
   * @throws Exception On errors
   */

  @Test
  fun testACSMCopyInEPUB() {
    val handle0 =
      BookDRMInformationHandleACS(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB
      )

    val acsm = this.resource("adobe-token.xml")

    val info0 = handle0.setACSMFile(acsm)
    assertEquals(info0, handle0.info)
    assertEquals("epub-meta_adobe.acsm", info0.acsmFile?.name)
    assertArrayEquals(acsm.readBytes(), info0.acsmFile?.readBytes())
    assertEquals(null, info0.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_EPUB
        )
      assertEquals(info0, handle1.info)
    }

    val info1 = handle0.setACSMFile(null)
    assertEquals(info1, handle0.info)
    assertEquals(null, info1.acsmFile)
    assertEquals(null, info1.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_EPUB
        )
      assertEquals(info1, handle1.info)
    }
  }

  /**
   * Copying in an ACSM saves the ACSM.
   *
   * @throws Exception On errors
   */

  @Test
  fun testACSMCopyInPDF() {
    val handle0 =
      BookDRMInformationHandleACS(
        directory = this.directory0,
        format = BOOK_FORMAT_PDF
      )

    val acsm = this.resource("adobe-token.xml")

    val info0 = handle0.setACSMFile(acsm)
    assertEquals(info0, handle0.info)
    assertEquals("pdf-meta_adobe.acsm", info0.acsmFile?.name)
    assertArrayEquals(acsm.readBytes(), info0.acsmFile?.readBytes())
    assertEquals(null, info0.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_PDF
        )
      assertEquals(info0, handle1.info)
    }

    val info1 = handle0.setACSMFile(null)
    assertEquals(info1, handle0.info)
    assertEquals(null, info1.acsmFile)
    assertEquals(null, info1.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_PDF
        )
      assertEquals(info1, handle1.info)
    }
  }

  /**
   * Setting a loan saves the loan.
   *
   * @throws Exception On errors
   */

  @Test
  fun testLoanSetEPUB() {
    val handle0 =
      BookDRMInformationHandleACS(
        directory = this.directory0,
        format = BOOK_FORMAT_EPUB
      )

    val data =
      ByteBuffer.allocate(23)
    val loan =
      AdobeAdeptLoan(
        AdobeLoanID("1e2869c2-1fd3-47d2-a5ac-a4e24093a64a"),
        data,
        true
      )

    val info0 = handle0.setAdobeRightsInformation(loan)
    assertEquals(info0, handle0.info)
    assertEquals(null, info0.acsmFile)
    assertEquals(Pair(File(directory0, "epub-rights_adobe.xml"), loan), info0.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_EPUB
        )
      assertEquals(info0, handle1.info)
    }

    val info1 = handle0.setAdobeRightsInformation(null)
    assertEquals(info1, handle0.info)
    assertEquals(null, info1.acsmFile)
    assertEquals(null, info1.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_EPUB
        )
      assertEquals(info1, handle1.info)
    }
  }

  /**
   * Setting a loan saves the loan.
   *
   * @throws Exception On errors
   */

  @Test
  fun testLoanSetPDF() {
    val handle0 =
      BookDRMInformationHandleACS(
        directory = this.directory0,
        format = BOOK_FORMAT_PDF
      )

    val data =
      ByteBuffer.allocate(23)
    val loan =
      AdobeAdeptLoan(
        AdobeLoanID("1e2869c2-1fd3-47d2-a5ac-a4e24093a64a"),
        data,
        true
      )

    val info0 = handle0.setAdobeRightsInformation(loan)
    assertEquals(info0, handle0.info)
    assertEquals(null, info0.acsmFile)
    assertEquals(Pair(File(directory0, "pdf-rights_adobe.xml"), loan), info0.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_PDF
        )
      assertEquals(info0, handle1.info)
    }

    val info1 = handle0.setAdobeRightsInformation(null)
    assertEquals(info1, handle0.info)
    assertEquals(null, info1.acsmFile)
    assertEquals(null, info1.rights)

    run {
      val handle1 =
        BookDRMInformationHandleACS(
          directory = this.directory0,
          format = BOOK_FORMAT_PDF
        )
      assertEquals(info1, handle1.info)
    }
  }

  private fun resource(
    name: String
  ): File {
    return BookDRMInformationHandleACSContract::class.java.getResourceAsStream(
      "/org/nypl/simplified/tests/books/$name"
    ).use { stream ->
      val out = File(this.directory1, name)
      FileUtilities.fileWriteStream(out, stream)
      out
    }
  }
}
