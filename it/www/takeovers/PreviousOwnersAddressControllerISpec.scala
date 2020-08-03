
package www.takeovers

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import controllers.takeovers.PreviousOwnersAddressController._
import fixtures.Fixtures
import forms.takeovers.HomeAddressForm.homeAddressKey
import itutil.servicestubs.{ALFStub, BusinessRegistrationStub, TakeoverStub}
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models._
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
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

  "show" should {
    "display the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubRetrieveCRCompanyDetails(testRegId, OK, Json.toJson(testCompanyDetails).toString())
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testPreviousOwnersAddress)
      stubGetPrepopAddresses(testRegId, OK, Nil)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status shouldBe OK
    }

    "display and prepop the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails.copy(previousOwnersAddress = Some(testPreviousOwnersAddress))))
      stubRetrieveCRCompanyDetails(testRegId, OK, Json.toJson(testCompanyDetails).toString())
      stubValidateRegisteredOfficeAddress(testCompanyDetails.cHROAddress, OK, testPreviousOwnersAddress)
      stubGetPrepopAddresses(testRegId, OK, Seq(testPreviousOwnersAddress))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get()
      )

      res.status shouldBe OK
      Jsoup.parse(res.body).getElementById("homeAddress-0").attr("checked") shouldBe "checked"
    }
  }

  "submit" should {
    "redirect to who agreed takeover page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(previousOwnersAddress = Some(testPreviousOwnersAddress)))

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testPreviousOwnersAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.submit().url)
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(homeAddressKey -> Seq("0")))
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.reg.routes.AccountingDatesController.show().url)
    }

    "redirect to ALF" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubInitAlfJourney(redirectLocation = "/test")

      val sessionCookie: String = getSessionCookie(
        Map("csrfToken" -> csrfToken,
          addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString()
        ), userId)

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.PreviousOwnersAddressController.submit().url)
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).post(Map(homeAddressKey -> Seq("Other")))
      )


      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain("/test")

      val onRampConfig: AlfJourneyConfig = getPOSTRequestJsonBody("/api/v2/init").as[AlfJourneyConfig]

      val expectedConfig: AlfJourneyConfig = AlfJourneyConfig(
        version = AlfJourneyConfig.defaultConfigVersion,
        options = JourneyOptions(
          continueUrl = "http://localhost:9970/register-your-company/save-alf-home-address-takeovers",
          homeNavHref = "http://www.hmrc.gov.uk/",
          accessibilityFooterUrl = "http://localhost:9970/register-your-company/accessibility-statement?pageUri=%2F",
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
            title = "Find the address",
            heading = s"Find $testPreviousOwnersName’s home address",
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
            title = "Confirm the address",
            heading = s"Confirm $testPreviousOwnersName’s home address",
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
    "redirect to accounting dates page" in {
      val testAlfId = "testAlfId"

      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
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
        .withHeaders(
          HeaderNames.COOKIE -> sessionCookie,
          "Csrf-Token" -> "nocheck"
        ).get()
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.reg.routes.AccountingDatesController.show().url)
    }
  }
}
