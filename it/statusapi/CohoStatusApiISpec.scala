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
package statusapi

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import play.api.test.FakeApplication
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}

class CohoStatusApiISpec extends IntegrationSpecBase with FakeAppConfig with LoginStub {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)
  private def intClient(path: String) = ws.url(s"http://localhost:$port/internal$path").withFollowRedirects(false)

  "Submission status proxy" should {
    "on 416 result from Coho, should proxy through 416" in {
      setupSimpleAuthMocks()

      stubFor(get(urlMatching("/check-submission\\?.*"))
        .withQueryParam("items_per_page",equalTo("1"))
        .willReturn(
          aResponse().
            withStatus(416).
            withBody("""{"x":2}""")
        )
      )

      val response = intClient("/check-submission").withQueryString("items_per_page"->"1").get.futureValue
      response.status shouldBe 416
    }
  }

  "on 200 result from Coho, proxy the response" in {
    setupSimpleAuthMocks()
    
    stubFor(get(urlMatching("/check-submission\\?.*"))
      .withQueryParam("items_per_page",equalTo("1"))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody("""{"x":1}""")
      )
    )

    val response = intClient("/check-submission").withQueryString("items_per_page"->"1").get.futureValue
    response.status shouldBe 200
    response.json shouldBe Json.parse("""{"x":1}""")
  }
}