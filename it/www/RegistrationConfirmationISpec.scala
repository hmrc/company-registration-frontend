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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub, WiremockHelper}
import models.connectors.ConfirmationReferences
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.ws.WS
import play.api.test.FakeApplication

class RegistrationConfirmationISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"
  val confirmationEncryptedRequest = "/registration-confirmation?request=eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..fc5Rl2" +
    "24BB_ZZRSTNXefWw.5FT1fmnDh6ptqlxo0D7oaMiAGOT35bt-RNf5HwyDDkLofd6JSNONcPR-O-nbOE43qrtIM40C6RZfF2oQwh4KcImvdzbSRa" +
    "cXDwxN-nTX-ULYFRuKTzKCpsojNsPv-kEe4SVBzFGB77z6fQ_zKKvp4355sG-wnVK2aQNzXy9Pe5n6SCK2bnKxsLPxEDkhfZvbaceUHs3FwzxTi" +
    "MKiTlVaURcGJjHn-2pWJdrfNpEfO-iCqfbl-JYNcgi4-z05QJ1ZqGB70ILwL07EdhNJR1vZD8OHLBKMc7eaUS_N8GRLbN9uAEovT8M6LK-R4mAn" +
    "277sGmXe0fv2nzLcUpHHPfu6VeQZftX7JltrbhoJy7TyYeZSMhw7aUboUcxeVcMcRkMz.B7YzOSxg0DJE4bWbQi3Y2A"

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

  "HO6" should {

    "Return a redirect to a new page when not authenticated" in {
      val response = await(client("/registration-confirmation?request=xxx").get())

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/application-not-complete")
    }

    "updating confirmation references successfully should return a confirmation" in {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, "5")

      val crResponse =
        """
          |{
          |"acknowledgement-reference" : "TEST-ACKREF",
          |"payment-reference" : "TEST-PAYMENTREF",
          |"payment-amount": "12",
          |"transaction-id" : "1551551"
          |}""".stripMargin
      stubPut("/company-registration/corporation-tax-registration/5/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/confirmation")
    }

    "updating confirmation references with 502 should return a retry page" in {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, "5")
      stubPut("/company-registration/corporation-tax-registration/5/confirmation-references", 502, "")

      val fResponse = client(confirmationEncryptedRequest).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/submission-failure")
    }

    "updating confirmation references with 403 should return a deskpro page" in {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, "5")
      stubPut("/company-registration/corporation-tax-registration/5/confirmation-references", 403, "")

      val fResponse = client(confirmationEncryptedRequest).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/something-went-wrong")
    }
  }


}