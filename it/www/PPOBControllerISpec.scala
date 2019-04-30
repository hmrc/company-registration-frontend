
package www

import java.util.UUID

import fixtures.Fixtures
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, RequestsFinder}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication

class PPOBControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with Fixtures with RequestsFinder {


  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
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
    stubPost("/api/init", 200, "{}", responseHeader = ("Location","foo"))
   val fResponse = buildClient(controllers.reg.routes.PPOBController.submit.url)
      .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map("Csrf-Token" -> Seq("nocheck"), "addressChoice" -> Seq("Other")))

    await(fResponse).status shouldBe 303
    getPOSTRequestJsonBody("/api/init") shouldBe Json.parse (
      s"""
        |{"continueUrl":"http://localhost:9970${controllers.reg.routes.PPOBController.saveALFAddress().url}",
        |"homeNavHref":"http://www.hmrc.gov.uk/",
        |"navTitle":"Set up a limited company and register for Corporation Tax",
        |"showPhaseBanner":true,"alphaPhase":false,
        |"phaseBannerHtml":"This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
        |"includeHMRCBranding":false,
        |"showBackButtons":true,
        |"deskProServiceName":"SCRS",
        |"timeout":{"timeoutAmount":999999,"timeoutUrl":"http://localhost:9970/register-your-company/error/timeout"},
        |"lookupPage":{"title":"Find the address","heading":"Find the address where the company will carry out most of its business activities","filterLabel":"Property name or number","submitLabel":"Find address","manualAddressLinkText":"Enter address manually"},
        |"selectPage":{"title":"Choose an address","heading":"Choose an address","proposalListLimit":30,"showSearchAgainLink":true,"searchAgainLinkText":"Search again","editAddressLinkText":"The address is not on the list"},"editPage":{"title":"Enter an address","heading":"Enter an address","line1Label":"Address line 1","line2Label":"Address line 2","line3Label":"Address line 3","showSearchAgainLink":true},
        |"confirmPage":{"title":"Confirm the address","heading":"Confirm where the company will carry out most of its business activities","showSubHeadingAndInfo":false,"submitLabel":"Confirm and continue","showSearchAgainLink":false,"showChangeLink":true,"changeLinkText":"Change"}
        |}
      """.stripMargin)

  }
}
