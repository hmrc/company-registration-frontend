
package filters

import itutil._
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder

class SessionIdFilterISpec extends IntegrationSpecBase
  with LoginStub
  with BeforeAndAfterEach
  with WiremockHelper
  with FakeAppConfig
  with MessagesHelper {


  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure(fakeConfig())
    .build()

  val regId = "reg-id-12345"

  "Loading the welcome page" should {

    "redirect to post-sign-in when an invalid sessionId exists" in {
      val response = await(buildClient("/register")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie(sessionId = invalidSessionId))
        .get())

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn(None).url)
    }

    "successfully load the page when a valid session id exists" in {
      val response = await(buildClient("/register")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie())
        .get())

      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.title() shouldBe messages("page.reg.welcome.description")
    }
  }
}