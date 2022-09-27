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

package filters

import itutil._
import org.jsoup.Jsoup
import play.api.{Application, Environment, Mode}
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.DefaultCookieSigner

class SessionIdFilterISpec extends IntegrationSpecBase
  with LoginStub
  with MessagesHelper {

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  override def beforeEach(): Unit = {}

  val regId = "reg-id-12345"

  "Loading the returning user page" should {
    "redirect to post-sign-in when an invalid sessionId exists" in {
      stubAudit

      val response = await(buildClient("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie(sessionId = invalidSessionId))
        .get())

      response.status mustBe 303
      response.header(HeaderNames.LOCATION) mustBe Some(controllers.reg.routes.SignInOutController.postSignIn(None).url)
    }

    "successfully load the page when a valid session id exists" in {
      stubAudit

      val response = await(buildClient("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> getSessionCookie())
        .get())

      response.status mustBe 200

      val document = Jsoup.parse(response.body)
      document.title must include(messages("page.reg.returningUser.title"))
    }
  }
}