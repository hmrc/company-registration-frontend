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
import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import play.api.http.HeaderNames
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeApplication
import play.modules.reactivemongo.ReactiveMongoComponent
import repositories.NavModelRepo
import uk.gov.hmrc.mongo.MongoSpecSupport
import utils.Jwe

import scala.concurrent.ExecutionContext.Implicits.global

class RegistrationConfirmationISpec extends IntegrationSpecBase with MongoSpecSupport with LoginStub with FakeAppConfig {

  val mockHost = WiremockHelper.wiremockHost
  val mockPort = WiremockHelper.wiremockPort
  val testkey = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig("microservice.services.JWE.key" -> testkey))

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  val userId = "/bar/foo"
  val regId = "regId5"

  class Setup {
    val rc = app.injector.instanceOf[ReactiveMongoComponent]
    val repo = new NavModelRepo(rc)
    //await(repo.repository.drop)
    await(repo.repository.ensureIndexes)
  }
  def confirmationEncryptedRequest(encrypted : String) = s"/registration-confirmation?request=$encrypted"

  val forwardPayloadString =
    s"""
       |{
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "ct_reference" : "TEST-ACKREF",
       |  "hmrc" : {},
       |  "ch" : {},
       |  "links" : {
       |    "forward" : "link-to-confirmation-on-ct"
       |  }
       |}
      """.stripMargin

  val forwardPayloadWithChString =
    s"""
       |{
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "ct_reference" : "TEST-ACKREF",
       |  "hmrc" : {},
       |  "ch" : {
       |    "data" : "test-data"
       |  },
       |  "links" : {
       |    "forward" : "link-to-confirmation-on-ct"
       |  }
       |}
      """.stripMargin

  val forwardPayloadJson = Json.parse(forwardPayloadString).as[JsObject]
  val forwardPayloadWithChJson = Json.parse(forwardPayloadWithChString).as[JsObject]

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender17373737373",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        ),
        "5-2" -> NavLinks(
          "link-to-confirmation-on-ct",
          ""
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )
  def stubKeystore(session: String, regId: String): StubMapping = {
    val keystoreUrl = s"/keystore/company-registration-frontend/${session}"
    stubFor(get(urlMatching(keystoreUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            s"""{
               |"id": "${session}",
               |"data": {
               |    "registrationID": "${regId}" }
               |}""".stripMargin
          )
      )
    )
  }

  "HO5-1" should {

    val transID = "1551552"
    lazy val encryptedForwardPayload = Jwe.encrypt(RegistrationConfirmationPayload(
      userId,
      "journeyid",
      transID,
      None,
      None,
      Json.obj(),
      Json.obj(),
      Json.obj("forward" -> "/link-to-before-you-pay-coho")
    ))

    "redirect to the forward url if there is a 200 on submission" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      val crResponse =
        s"""
           |{
           |"acknowledgement-reference" : "TEST-ACKREF",
           |"transaction-id" : "$transID"
           |}""".stripMargin
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest(encryptedForwardPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString  = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson  = Jwe.decrypt[JsObject](encryptedHandOffString).get


      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/link-to-before-you-pay-coho")
      decryptedHandoffJson shouldBe forwardPayloadJson
    }

    "redirect with the same ch data that was recieved" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val encryptedForwardWithChPayload = Jwe.encrypt(RegistrationConfirmationPayload(
        userId,
        "journeyid",
        transID,
        None,
        None,
        Json.obj("data" -> "test-data"),
        Json.obj(),
        Json.obj("forward" -> "/link-to-before-you-pay-coho")
      ))


      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      val crResponse =
        s"""
           |{
           |"acknowledgement-reference" : "TEST-ACKREF",
           |"transaction-id" : "$transID"
           |}""".stripMargin
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest(encryptedForwardWithChPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString  = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson  = Jwe.decrypt[JsObject](encryptedHandOffString).get

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/link-to-before-you-pay-coho")
      decryptedHandoffJson shouldBe forwardPayloadWithChJson
    }

    "redirect to the forward url if there is a 502 on submission" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)


      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      val crResponse =
        s"""
           |{
           |"acknowledgement-reference" : "TEST-ACKREF",
           |"transaction-id" : "$transID"
           |}""".stripMargin
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 502, crResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest(encryptedForwardPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString  = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson  = Jwe.decrypt[JsObject](encryptedHandOffString).get

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/link-to-before-you-pay-coho")
      decryptedHandoffJson shouldBe forwardPayloadJson
    }

    "redirect to the forward url if there is 403 on submission" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      val crResponse =
        s"""
           |{
           |"acknowledgement-reference" : "TEST-ACKREF",
           |"transaction-id" : "$transID"
           |}""".stripMargin
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 403, crResponse)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest(encryptedForwardPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString  = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson  = Jwe.decrypt[JsObject](encryptedHandOffString).get

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/link-to-before-you-pay-coho")
      decryptedHandoffJson shouldBe forwardPayloadJson
    }
  }

  "HO6" should {

    val transID = "1551551"
    val paymentRef = "TEST-PAYMENTREF"
    val paymentAmount = "12"

    lazy val encryptedPayload = Jwe.encrypt(RegistrationConfirmationPayload(
      userId,
      "journeyid",
      transID,
      Some(paymentRef),
      Some(paymentAmount),
      Json.obj(),
      Json.obj(),
      Json.obj()
    ))

    "Return a redirect to a new page when not authenticated" in new Setup {
      val response = await(client("/registration-confirmation?request=xxx").get())

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/application-not-complete")
    }

    "updating confirmation references successfully should return a confirmation" in new Setup{
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))

      val crResponse =
        s"""
          |{
          |"acknowledgement-reference" : "TEST-ACKREF",
          |"payment-reference" : "$paymentRef",
          |"payment-amount": "$paymentAmount",
          |"transaction-id" : "$transID"
          |}""".stripMargin
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 200, crResponse)

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/confirmation")
    }

    "updating confirmation references with 502 should return a retry page" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 502, "")

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/submission-failure")
    }

    "updating confirmation references with 403 should return a deskpro page" in new Setup {
      setupSimpleAuthMocks()
      stubSuccessfulLogin(userId=userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)
      await(repo.repository.insertNavModel(regId,handOffNavModel))
      stubPut(s"/company-registration/corporation-tax-registration/$regId/confirmation-references", 403, "")

      val fResponse = client(confirmationEncryptedRequest(encryptedPayload.get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/something-went-wrong")
    }
  }


}