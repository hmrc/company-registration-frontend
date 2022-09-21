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
import controllers.takeovers.OtherBusinessAddressController._
import fixtures.Fixtures
import forms.takeovers.OtherBusinessAddressForm.otherBusinessAddressKey
import itutil.servicestubs.{ALFStub, BusinessRegistrationStub, TakeoverStub}
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models._
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class OtherBusinessAddressControllerISpec extends IntegrationSpecBase
  with LoginStub
  with MockitoSugar
  with RequestsFinder
  with TakeoverStub
  with Fixtures
  with BusinessRegistrationStub
  with ALFStub {

  val userId: String = "testUserId"
  val testRegId: String = "testRegId"
  val testBusinessName: String = "test name"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName))
  val testCompanyDetails: CompanyDetails = CompanyDetails(
    "CompanyName",
    CHROAddress("premises", "testLine1", Some("testLine1"), "locality", "testCountry", None, Some("Z11 11Z"), None),
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
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testBusinessAddress)
      stubGetPrepopAddresses(testRegId, OK, Nil)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status shouldBe OK
    }

    "display and prepop the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails.copy(businessTakeoverAddress = Some(testBusinessAddress))))
      stubRetrieveCRCompanyDetails(testRegId, OK, Json.toJson(testCompanyDetails).toString())
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testBusinessAddress)
      stubGetPrepopAddresses(testRegId, OK, Seq(testBusinessAddress))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status shouldBe OK
      Jsoup.parse(res.body).getElementById("test name").attr("value") shouldBe "0"
    }
  }

  "submit" should {
    "redirect to who agreed takeover page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(businessTakeoverAddress = Some(testBusinessAddress)))

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessAddressController.submit.url)
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(otherBusinessAddressKey -> Seq("0")))
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
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

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessAddressController.submit.url)
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(otherBusinessAddressKey -> Seq("Other")))
      )


      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain("/test")

      val onRampConfig: AlfJourneyConfig = getPOSTRequestJsonBody("/api/v2/init").as[AlfJourneyConfig]

      val expectedConfig: AlfJourneyConfig = AlfJourneyConfig(
        version = AlfJourneyConfig.defaultConfigVersion,
        options = JourneyOptions(
          continueUrl = "http://localhost:9970/register-your-company/save-alf-address-takeovers",
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
            navTitle = "Set up a limited company and register for Corporation Tax",
            phaseBannerHtml = "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>."
          ),
          SelectPageLabels(
            title = "Choose an address",
            heading = s"Choose an address",
            searchAgainLinkText = "Search again",
            editAddressLinkText = "The address is not on the list"
          ),
          LookupPageLabels(
            title = s"Find $testBusinessName’s address",
            heading = s"Find $testBusinessName’s address",
            filterLabel = "Property name or number",
            submitLabel = "Find address",
            manualAddressLinkText = "Enter address manually"
          ),
          EditPageLabels(
            title = "Enter an address",
            heading = "Enter an address",
            line1Label = "Address line 1",
            line2Label = "Address line 2",
            line3Label = "Address line 3"
          ),
          ConfirmPageLabels(
            title = s"Confirm $testBusinessName’s address",
            heading = s"Confirm $testBusinessName’s address",
            submitLabel = "Confirm and continue",
            changeLinkText = "Change"
          )
        )
      )
      )
      onRampConfig shouldBe expectedConfig
    }
  }

  "handbackFromALF" should {
    "redirect to who agreed takeover page" in {
      val testAlfId = "testAlfId"

      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(businessTakeoverAddress = Some(testBusinessAddress.copy(postcode = None))))
      stubPost(url = s"/business-registration/$testRegId/addresses", 200, Json.toJson(testBusinessAddress).toString)

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
          addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.OtherBusinessAddressController.handbackFromALF(Some(testAlfId)).url)
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).get()
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
    }
  }
}
