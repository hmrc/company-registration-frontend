/*
 * Copyright 2017 HM Revenue & Customs
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
package www

import java.util.UUID

import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import org.scalatest.BeforeAndAfterEach
import play.api.http.HeaderNames
import play.api.libs.ws.WSResponse
import play.api.test.FakeApplication

import scala.concurrent.Future

class ReturningUserControllerISpec extends IntegrationSpecBase with LoginStub with BeforeAndAfterEach with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/wibble"

  "POST /setting-up-new-limited-company" should {
    val map = Map(
      "csrfToken"-> Seq("xxx-ignored-xxx"),
      "returningUser" -> Seq("true")
    )

    "redirect when starting a new registration to creating a new account if the signposting is disabled" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      setupFeatures()

      val post: Future[WSResponse] = client("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .post(map)
      val response = await(post)

      response.status shouldBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/government-gateway-registration-frontend")
        r should include("origin=company-registration-frontend")
        r should include("register-your-company%2Fpost-sign-in")
      }
    }

    "redirect when starting a new registration to company registration eligibility frontend if the signposting enabled" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      setupFeatures(signPosting = true)

      val post: Future[WSResponse] = client("/setting-up-new-limited-company")
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .post(map)
      val response = await(post)

      response.status shouldBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/eligibility-for-setting-up-company")
      }
    }
  }
}