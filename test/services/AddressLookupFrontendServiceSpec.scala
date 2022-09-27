/*
 * Copyright 2022 HM Revenue & Customs
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

import fixtures.AddressFixture
import helpers.UnitSpec
import mocks.{AddressLookupConfigBuilderServiceMock, SCRSMocks, TakeoverServiceMock}
import models._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Messages, MessagesApi, MessagesProvider}
import play.api.mvc.Call
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class AddressLookupFrontendServiceSpec()(implicit messagesProvider: MessagesProvider) extends UnitSpec
  with MockitoSugar
  with AddressFixture
  with GuiceOneAppPerSuite
  with SCRSMocks
  with TakeoverServiceMock
  with AddressLookupConfigBuilderServiceMock {


  class Setup {
    val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

    object TestService extends AddressLookupFrontendService(
      mockAddressLookupConnector,
      mockAppConfig,
      mockAddressLookupConfigBuilderService,
      messagesApi
    ) {
      override lazy val companyRegistrationFrontendURL = "testCompanyRegUrl"
      override lazy val timeoutInSeconds = 22666
    }

  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getAddress" should {
    val id = "testID"
    val address = validNewAddress

    "return an address" in new Setup {
      when(mockAddressLookupConnector.getAddress(eqTo(id))(any()))
        .thenReturn(Future.successful(address))

      val result: NewAddress = await(TestService.getAddress(id)(hc))

      result mustBe address
    }
  }

  "initialiseAlfJourney" should {
    "return a url" in new Setup {
      val url = "testID"
      val testHandbackLocation: Call = Call("", "/testUrl")

      val config: AlfJourneyConfig = AlfJourneyConfig(
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
            proposalListLimit = 1,
            showSearchAgainLink = true
          ),

          confirmPageConfig = ConfirmPageConfig(
            showSearchAgainLink = true,
            showSubHeadingAndInfo = false,
            showChangeLink = true
          ),

          timeoutConfig = TimeoutConfig(
            timeoutAmount = 22666,
            timeoutUrl = "testCompanyRegUrl/register-your-company/error/timeout"
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

      mockBuildLegacyConfig(
        handbackLocation = testHandbackLocation,
        specificJourneyKey = "takeovers",
        lookupPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.lookup.heading", "testBusinessName"),
        confirmPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.confirm.description", "testBusinessName")
      )(response = config)

      mockGetOnRampUrl(config)(Future.successful(url))

      val res: String = await(TestService.initialiseAlfJourney(
        handbackLocation = testHandbackLocation,
        specificJourneyKey = "takeovers",
        lookupPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.lookup.heading", "testBusinessName"),
        confirmPageHeading = Messages("page.addressLookup.takeovers.otherBusinessAddress.confirm.description", "testBusinessName")
      ))

      res mustBe url
    }
  }

}
