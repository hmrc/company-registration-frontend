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

package test.www

import com.github.tomakehurst.wiremock.client.WireMock._
import test.itutil.{IntegrationSpecBase, LoginStub}
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner

import scala.concurrent.duration.FiniteDuration

class VerifyYourEmailISpec extends IntegrationSpecBase with LoginStub {

  val userId = "/wibble"
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  def stubKeystore(session: String, regId: String, email: String) = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": {
               |  "registrationID": "${regId}",
               |  "email": "${email}"
               |}
               |}""".stripMargin
          )
      )
    )
  }

  "GET /ryc/verify-your-email" should {
    "Show a page with the email when logged in" in {
      stubAuthorisation()
      val emailResponseFromCr =
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": false,
          |  "verified": false,
          |  "return-link-email-sent" : false
          |
          | }
        """.stripMargin
      val email = "foo@bar.wibble"
      stubKeystore(SessionId, "5", email)
      stubGet("/company-registration/corporation-tax-registration/5/retrieve-email", 200, emailResponseFromCr)
      val fResponse = buildClient("/sent-an-email").
        withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId)).
        get()

      val response = await(fResponse)(FiniteDuration(10, "seconds"))
      response.status mustBe 200

      val document = Jsoup.parse(response.body)
      document.title must include("Confirm your email address")
      document.select("p").text must include(email)
    }

    "redirect to sign-in when not logged in" in {
      stubAuthorisation(401, None)

      val response = await(buildClient("/sent-an-email").get())

      response.status mustBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo mustBe defined
      redirectTo map { r =>
        r must include("/bas-gateway/sign-in")
        r must include("register-your-company%2Fpost-sign-in")
      }
    }
  }
}