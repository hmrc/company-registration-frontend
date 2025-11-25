/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.UnitSpec
import mocks.AppConfigMock
import models._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi, MessagesProvider}
import play.api.mvc.Call

class AddressLookupConfigBuilderServiceSpec()(implicit messagesProvider: MessagesProvider) extends UnitSpec with GuiceOneAppPerSuite with AppConfigMock {

  implicit val messages: MessagesApi = app.injector.instanceOf[MessagesApi]

  object TestService extends AddressLookupConfigBuilderService(mockAppConfig) {
    override lazy val companyRegistrationFrontendURL = "testCompanyRegUrl"
    override lazy val timeoutLength = 22666
    override lazy val accessibilityFooterUrl = "testCompanyRegUrl/register-your-company/accessibility-statement?pageUri=%2F?service=address-lookup&userAction=lookup"
  }

  "buildConfig" should {
    "return a filled AlfJourneyConfig model" in {

      val result: AlfJourneyConfig = TestService.buildConfig(
        handbackLocation = Call("GET", "/foo"),
        specificJourneyKey = "PPOB",
        lookupPageHeading = Messages("page.addressLookup.PPOB.lookup.heading"),
        confirmPageHeading = Messages("page.addressLookup.PPOB.confirm.description")
      )

      val expectedConfig: AlfJourneyConfig = AlfJourneyConfig(

        version = AlfJourneyConfig.defaultConfigVersion,
        options = JourneyOptions(
          continueUrl = "testCompanyRegUrl/foo",
          homeNavHref = "http://www.hmrc.gov.uk/",
          accessibilityFooterUrl = "testCompanyRegUrl/register-your-company/accessibility-statement?pageUri=%2F?service=address-lookup&userAction=lookup",
          deskProServiceName = "SCRS",
          showPhaseBanner = true,
          alphaPhase = false,
          showBackButtons = true,
          includeHMRCBranding = false,
          disableTranslations = true,

          selectPageConfig = SelectPageConfig(
            proposalListLimit = 30,
            showSearchAgainLink = true
          ),

          confirmPageConfig = ConfirmPageConfig(
            showSearchAgainLink = false,
            showSubHeadingAndInfo = false,
            showChangeLink = true
          ),

          timeoutConfig = TimeoutConfig(
            timeoutAmount = 22666,
            timeoutUrl = "testCompanyRegUrl/register-your-company/error/timeout"
          ),

          manualAddressEntryConfig = ManualAddressEntryConfig(
            mandatoryFields = MandatoryFields(
              addressLine1 = true,
              addressLine2 = true,
              addressLine3 = false,
              town         = false,
              postcode     = true
            )
          )
        ),

        labels = JourneyLabels(en = LanguageLabels(
          appLevelLabels = AppLevelLabels(
            navTitle = Some("Set up a limited company and register for Corporation Tax"),
            phaseBannerHtml = None
          ),

          SelectPageLabels(
            title = Some("Choose an address"),
            heading = Some("Choose an address"),
            searchAgainLinkText = Some("Search again"),
            editAddressLinkText = Some("The address is not on the list")
          ),

          LookupPageLabels(
            title = None,
            heading = None,
            filterLabel = Some("Property name or number"),
            submitLabel = Some("Find address"),
            manualAddressLinkText = Some("Enter address manually")
          ),
          EditPageLabels(
            title = Some("Enter an address"),
            heading = Some("Enter an address"),
            line1Label = Some("Address line 1"),
            line2Label = Some("Address line 2"),
            line3Label = Some("Address line 3")
          ),
          ConfirmPageLabels(
            title = None,
            heading = None,
            submitLabel = Some("Confirm and continue"),
            changeLinkText = Some("Change")
          )
        ), cy = LanguageLabels(
          appLevelLabels = AppLevelLabels(
            navTitle = Some("Sefydlu cwmni cofrestredig a chofrestru ar gyfer Treth Gorfforaeth"),
            phaseBannerHtml = None
          ),

          SelectPageLabels(
            title = Some("Dewiswch gyfeiriad"),
            heading = Some("Dewiswch gyfeiriad"),
            searchAgainLinkText = Some("Chwilio eto"),
            editAddressLinkText = Some("Nid yw’r cyfeiriad ar y rhestr")
          ),

          LookupPageLabels(
            title = None,
            heading = None,
            filterLabel = Some("Enw neu rif yr eiddo"),
            submitLabel = Some("Dod o hyd i’r cyfeiriad"),
            manualAddressLinkText = Some("Nodwch y cyfeiriad â llaw")
          ),
          EditPageLabels(
            title = Some("Nodwch gyfeiriad"),
            heading = Some("Nodwch gyfeiriad"),
            line1Label = Some("Cyfeiriad - llinell 1"),
            line2Label = Some("Cyfeiriad - llinell 2"),
            line3Label = Some("Cyfeiriad - llinell 3")
          ),
          ConfirmPageLabels(
            title = None,
            heading = None,
            submitLabel = Some("Cadarnhau ac yn eich blaen"),
            changeLinkText = Some("Newid")
          )
        )
        )
      )

      result mustBe expectedConfig

    }
  }

}
