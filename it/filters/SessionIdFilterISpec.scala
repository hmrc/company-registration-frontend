
package filters

import itutil._
import org.jsoup.Jsoup
import play.api.http.HeaderNames

class SessionIdFilterISpec extends IntegrationSpecBase
  with LoginStub
  with MessagesHelper {

  override def beforeEach(): Unit = {}

  val regId = "reg-id-12345"

  "Loading the returning user page" should {
    "redirect to post-sign-in when an invalid sessionId exists" in {
      stubAudit

      val response = await(buildClient("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie(sessionId = invalidSessionId))
        .get())

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn(None).url)
    }

    "successfully load the page when a valid session id exists" in {
      stubAudit

      val response = await(buildClient("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie())
        .get())

      response.status shouldBe 200

      val document = Jsoup.parse(response.body)
      document.title should include(messages("page.reg.returningUser.title"))
    }
  }
}