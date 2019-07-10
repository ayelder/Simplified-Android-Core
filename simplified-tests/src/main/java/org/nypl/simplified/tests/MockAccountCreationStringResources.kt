package org.nypl.simplified.tests

import org.nypl.simplified.profiles.controller.api.ProfileAccountCreationStringResourcesType

class MockAccountCreationStringResources : ProfileAccountCreationStringResourcesType {

  override val creatingAccountFailed: String
    get() = "creatingAccountFailed"

  override val creatingAccount: String
    get() = "creatingAccount"

  override val resolvingAccountProviderFailed: String
    get() = "resolvingAccountProviderFailed"

  override val resolvingAccountProvider: String
    get() = "resolvingAccountProvider"


}