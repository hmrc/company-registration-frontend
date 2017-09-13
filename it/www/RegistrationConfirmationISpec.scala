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
import models.RegistrationConfirmationPayload
import models.connectors.ConfirmationReferences
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import utils.Jwe

class RegistrationConfirmationISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val testkey = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig("microservice.services.JWE.key" -> testkey))

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"
  def confirmationEncryptedRequest(encrypted : String) = s"/registration-confirmation?request=$encrypted"

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

    val transID = "1551551"
    val paymentRef = "TEST-PAYMENTREF"
    val paymentAmount = "12"

    lazy val encryptedPayload = Jwe.encrypt(RegistrationConfirmationPayload(
      userId,
      "journeyid",
      transID,
      paymentRef,
      paymentAmount,
      Json.obj(),
      Json.obj(),
      Json.obj()
    ))

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
        s"""
          |{
          |"acknowledgement-reference" : "TEST-ACKREF",
          |"payment-reference" : "$paymentRef",
          |"payment-amount": "$paymentAmount",
          |"transaction-id" : "$transID"
          |}""".stripMargin
      stubPut("/company-registration/corporation-tax-registration/5/confirmation-references", 200, crResponse)

      println(System.getProperty("microservice.services.JWE.key"))
      println("============================ " + app.configuration.getString("microservice.services.JWE.key"))

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
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

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
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

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/something-went-wrong")
    }
  }


}