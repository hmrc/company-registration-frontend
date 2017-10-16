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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import org.jsoup.Jsoup
import play.api.http.HeaderNames

class SignOutISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"

  def encodeURL(url: String) = java.net.URLEncoder.encode(url, "UTF-8")

  def setupSimpleAuthMocks(): StubMapping = {
    stubPost("/write/audit", 200, """{"x":2}""")
    stubGet("/auth/authority", 200,
      s"""
         |{
         |"uri":"${userId}",
         |"accounts":{},
         |"levelOfAssurance": "2",
         |"confidenceLevel" : 50,
         |"credentialStrength": "strong",
         |"legacyOid":"1234567890"
         |}""".stripMargin
    )
  }

  def stubKeystore(session: String, regId: String): StubMapping = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": { "registrationID": "${regId}" }
               |}""".stripMargin
          )
      )
    )
  }

  "Sign Out" should {
    "Return a redirect to GG sign out" in {
      val response = await(client("/sign-out").get())

      response.status shouldBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/gg/sign-out")
      }
    }

    "Return a redirect to GG sign out with relative continue URL" in {
      val continueURL = "/foo/bar"
      val response = await(client(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status shouldBe 303
      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/gg/sign-out")
        r should include(encodeURL(continueURL))
      }
    }

    "Return a redirect to GG sign out with absolute continue URL" in {
      val continueURL = "http://foo.gov.uk/foo/bar"
      val response = await(client(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status shouldBe 303
      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/gg/sign-out")
        r should include(encodeURL(continueURL))
      }
    }

    "Return a bad request if URL isn't valid" in {
      val continueURL = "//foo.gov.uk/foo/bar"
      val response = await(client(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status shouldBe 400
      val document = Jsoup.parse(response.body)
      document.title() shouldBe "Bad request - 400"
      document.select("h1").text should include("Bad request")
    }
  }
}
