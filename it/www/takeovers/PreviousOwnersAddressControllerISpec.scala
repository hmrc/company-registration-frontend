/*
 * Copyright 2021 HM Revenue & Customs
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

package www.takeovers

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import controllers.takeovers.PreviousOwnersAddressController._
import fixtures.Fixtures
import forms.takeovers.HomeAddressForm.homeAddressKey
import itutil.servicestubs.{ALFStub, BusinessRegistrationStub, TakeoverStub}
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models._
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class PreviousOwnersAddressControllerISpec extends IntegrationSpecBase
  with LoginStub
  with MockitoSugar
  with RequestsFinder
  with TakeoverStub
  with Fixtures
  with BusinessRegistrationStub
  with ALFStub {

  val userId: String = "testUserId"
  val testRegId: String = "testRegId"
  val testBusinessName: String = "test business name"
  val testPreviousOwnersName: String = "test name"
  val testBusinessAddress: NewAddress = NewAddress("BusinessAddressLine1", "BusinessAddressLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testPreviousOwnersAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName), Some(testBusinessAddress), Some(testPreviousOwnersName), None)
  val testCompanyDetails: CompanyDetails = CompanyDetails(
    "CompanyName",
    CHROAddress("premises", "BusinessAddressLine1", Some("BusinessAddressLine2"), "locality", "testCountry", None, Some("Z11 11Z"), None),
    PPOB("", None),
    "ENGLAND_AND_WALES"
  )

  lazy val csrfToken: String = UUID.randomUUID().toString
  lazy val sessionCookie: String = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  "show" should {
    "display the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubRetrieveCRCompanyDetails(testRegId, OK, Json.toJson(testCompanyDetails).toString())
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testPreviousOwnersAddress)
      stubGetPrepopAddresses(testRegId, OK, Nil)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status mustBe OK
    }

    "display and prepop the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails.copy(previousOwnersAddress = Some(testPreviousOwnersAddress))))
      stubRetrieveCRCompanyDetails(testRegId, OK, Json.toJson(testCompanyDetails).toString())
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testPreviousOwnersAddress)
      stubGetPrepopAddresses(testRegId, OK, Seq(testPreviousOwnersAddress))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status mustBe OK
    }
  }

  "submit" should {
    "redirect to who agreed takeover page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(previousOwnersAddress = Some(testPreviousOwnersAddress)))

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testPreviousOwnersAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.submit.url)
        .withHttpHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(homeAddressKey -> Seq("0")))
      )

      res.status mustBe SEE_OTHER
      res.redirectLocation must contain(controllers.reg.routes.AccountingDatesController.show.url)
    }

    "redirect to ALF" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubInitAlfJourney(redirectLocation = "/test")

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.submit.url)
        .withHttpHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(homeAddressKey -> Seq("Other")))
      )


      res.status mustBe SEE_OTHER
      res.redirectLocation must contain("/test")

      val onRampConfig: AlfJourneyConfig = getPOSTRequestJsonBody("/api/v2/init").as[AlfJourneyConfig]

      val expectedConfig: AlfJourneyConfig = AlfJourneyConfig(
        version = AlfJourneyConfig.defaultConfigVersion,
        options = JourneyOptions(
          continueUrl = "http://localhost:9970/register-your-company/save-alf-home-address-takeovers",
          homeNavHref = "http://www.hmrc.gov.uk/",
          accessibilityFooterUrl = "http://localhost:12346/accessibility-statement/company-registration",
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
            timeoutAmount = 999999,
            timeoutUrl = "http://localhost:9970/register-your-company/error/timeout"
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


      onRampConfig mustBe expectedConfig
    }
  }

  "handbackFromALF" should {
    "redirect to accounting dates page" in {
      val testAlfId = "testAlfId"

      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(previousOwnersAddress = Some(testPreviousOwnersAddress.copy(postcode = None))))
      stubPost(url = s"/business-registration/$testRegId/addresses", 200, Json.toJson(testPreviousOwnersAddress).toString)

      val addressLookupResponse: String = Json.obj(
        "auditRef" -> "tstAuditRef",
        "address" -> Json.obj(
          "lines" -> Seq(
            "testLine1",
            "testLine2"
          ),
          "postcode" -> "Z11 11Z",
          "country" -> Json.obj(
            "code" -> "TEST",
            "name" -> "testCountry"
          )
        )
      ).toString()

      stubFor(get(urlEqualTo(s"/api/confirmed?id=$testAlfId"))
        .willReturn(
          aResponse().
            withStatus(200).
            withBody(addressLookupResponse)
        )
      )

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testPreviousOwnersAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.handbackFromALF(Some(testAlfId)).url)
        .withHttpHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).get()
      )

      res.status mustBe SEE_OTHER
      res.redirectLocation must contain(controllers.reg.routes.AccountingDatesController.show.url)
    }
  }
}
