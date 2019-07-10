package org.nypl.simplified.accounts.api

/**
 * An interface exposing string resources for account provider resolution.
 */

interface AccountProviderResolutionStringsType {

  /**
   * Fetching an authentication document failed.
   */

  val resolvingAuthDocumentRetrievalFailed: String

  /**
   * The description of a COPPA age gate is malformed.
   */

  val resolvingAuthDocumentCOPPAAgeGateMalformed: String

  /**
   * The authentication document contained authentication types but we couldn't understand any of them.
   */

  val resolvingAuthDocumentNoUsableAuthenticationTypes: String

  /**
   * The authentication document was required to contain a starting URI, but didn't contain one.
   */

  val resolvingAuthDocumentNoStartURI: String

  /**
   * Could not parse the authentication document.
   */

  val resolvingAuthDocumentParseFailed: String

  /**
   * No authentication document URI was provided!
   */

  val resolvingAuthDocumentMissingURI: String

  /**
   * Resolving the account provider...
   */

  val resolving: String

  /**
   * Fetching the authentication document...
   */

  val resolvingAuthDocument: String
}