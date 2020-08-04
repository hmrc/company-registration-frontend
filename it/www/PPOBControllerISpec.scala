
package www

import java.util.UUID

import fixtures.Fixtures
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models._
import play.api.http.HeaderNames

class PPOBControllerISpec extends IntegrationSpecBase with LoginStub with Fixtures with RequestsFinder {
  val userId = "test-user-id"
  val regId = "12345"


  class Setup {
    val csrfToken = () => UUID.randomUUID().toString
    val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
  }

  "submit should hand off to ALF with the correct Json" in new Setup {

    stubSuccessfulLogin(userId = userId)
    stubFootprint(200, footprintResponse(regId))
    stubGet(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration", 200, statusResponseFromCR(rID = "12345"))

    stubKeystore(SessionId, regId)
    stubPost("/api/v2/init", 200, "{}", responseHeader = ("Location", "foo"))
    val fResponse = buildClient(controllers.reg.routes.PPOBController.submit.url)
      .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
      .post(Map("Csrf-Token" -> Seq("nocheck"), "addressChoice" -> Seq("Other")))

    await(fResponse).status shouldBe 303
    val result = getPOSTRequestJsonBody("/api/v2/init").as[AlfJourneyConfig]
    val expected = AlfJourneyConfig(
      version = AlfJourneyConfig.defaultConfigVersion,
      options = JourneyOptions(
        continueUrl = s"http://localhost:9970${controllers.reg.routes.PPOBController.saveALFAddress(None).url}",
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
          heading = "Choose an address",
          searchAgainLinkText = "Search again",
          editAddressLinkText = "The address is not on the list"
        ),

        LookupPageLabels(
          title = "Find the address where the company will carry out most of its business activities",
          heading = "Find the address where the company will carry out most of its business activities",
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
          title = "Confirm where the company will carry out most of its business activities",
          heading = "Confirm where the company will carry out most of its business activities",
          submitLabel = "Confirm and continue",
          changeLinkText = "Change"
        )
      )
      )

    )

    result shouldBe expected
  }

}
