/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import mocks.AppConfigMock
import models.{AlfJourneyConfig, ConfirmPageConfig, EditPageConfig, LookupPageConfig, SelectPageConfig, TimeoutConfig, TopLevelConfig}
import play.api.i18n.MessagesApi
import play.api.mvc.Call
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class AddressLookupConfigBuilderServiceSpec extends UnitSpec with WithFakeApplication with AppConfigMock {

  implicit val messages: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]

  object TestService extends AddressLookupConfigBuilderService(mockAppConfig) {
    override lazy val companyRegistrationFrontendURL = "testCompanyRegUrl"
    override lazy val timeoutLength = 22666
  }

  "buildConfig" should {
    "return a filled AlfJourneyConfig model" in {

      val result: AlfJourneyConfig = TestService.buildConfig(
        handbackLocation = Call("GET", "/foo"),
        specificJourneyKey = "PPOB",
        lookupPageHeading = messages("page.addressLookup.PPOB.lookup.heading"),
        confirmPageHeading = messages("page.addressLookup.PPOB.confirm.description")
      )

      val expectedConfig: AlfJourneyConfig = AlfJourneyConfig(
        topLevelConfig = TopLevelConfig(
          continueUrl = "testCompanyRegUrl/foo",
          homeNavHref = "http://www.hmrc.gov.uk/",
          navTitle = "Set up a limited company and register for Corporation Tax",
          showPhaseBanner = true,
          alphaPhase = false,
          phaseBannerHtml = "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
          includeHMRCBranding = false,
          showBackButtons = true,
          deskProServiceName = "SCRS"
        ),
        lookupPageConfig = LookupPageConfig(
          title = "Find the address",
          heading = "Find the address where the company will carry out most of its business activities",
          filterLabel = "Property name or number",
          submitLabel = "Find address",
          manualAddressLinkText = "Enter address manually"
        ),
        selectPageConfig = SelectPageConfig(
          title = "Choose an address",
          heading = "Choose an address",
          proposalListLimit = 30,
          showSearchAgainLink = true,
          searchAgainLinkText = "Search again",
          editAddressLinkText = "The address is not on the list"
        ),
        editPageConfig = EditPageConfig(
          title = "Enter an address",
          heading = "Enter an address",
          line1Label = "Address line 1",
          line2Label = "Address line 2",
          line3Label = "Address line 3",
          showSearchAgainLink = true
        ),
        confirmPageConfig = ConfirmPageConfig(
          title = "Confirm the address",
          heading = "Confirm where the company will carry out most of its business activities",
          showSubHeadingAndInfo = false,
          submitLabel = "Confirm and continue",
          showSearchAgainLink = false,
          showChangeLink = true,
          changeLinkText = "Change"
        ),
        timeoutConfig = TimeoutConfig(
          timeoutAmount = 22666,
          timeoutUrl = "testCompanyRegUrl/register-your-company/error/timeout"
        )
      )

      result shouldBe expectedConfig

    }
  }

}
