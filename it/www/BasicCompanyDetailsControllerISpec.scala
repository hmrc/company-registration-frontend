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

package www

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.{IntegrationSpecBase, LoginStub}
import models.handoff._
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}
import repositories.NavModelRepoImpl
import utils.JweCommon

import java.util.UUID

class BasicCompanyDetailsControllerISpec extends IntegrationSpecBase with LoginStub {

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val userId = "/bar/foo"
  val regId = "regId5"

  class Setup {
    val repo = app.injector.instanceOf[NavModelRepoImpl].repository
    await(repo.ensureIndexes)
  }

  def returnEncryptedRequest(encrypted: String) = s"/return-to-about-you?request=$encrypted"

  val forwardPayloadString =
    s"""
       |{
       |  "email_address" : "fudge@fromcomgreg.com",
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "name" : "name",
       |  "hmrc" : {},
       |  "session" : {
       |    "timeout" : 999939,
       |    "keepalive_url" : "http://localhost:9970${controllers.reg.routes.SignInOutController.renewSession.url}",
       |    "signedout_url" : "http://localhost:9970${controllers.reg.routes.SignInOutController.destroySession.url}"
       |  },
       |  "links" : {
       |    "forward" : "link-to-about-you",
       |    "reverse" : "link-to-principal-place"
       |  }
       |}
      """.stripMargin

  val forwardPayloadJson = Json.parse(forwardPayloadString).as[JsObject]

  val returnPaylodString =
    s"""
       |{
       |  "user_id" : "Ext-xxx",
       |  "journey_id" : "$regId",
       |  "hmrc" : {},
       |  "ch" : {},
       |  "links" : {}
       |}
     """.stripMargin

  val returnPayloadJson = Json.parse(forwardPayloadString).as[JsObject]

  val handOffNavModel = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "link-to-about-you",
          "link-to-principal-place"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "/initial-coho-link",
          "testReverseLinkFromReceiver0"
        )
      )
    )
  )

  val userDetails =
    s"""
       |{
       |  "name":"name",
       |  "email":"test@me.com",
       |  "affinityGroup" : "affinityGroup",
       |  "description" : "description",
       |  "lastName":"test",
       |  "dateOfBirth":"1980-06-30",
       |  "postCode":"NW94HD",
       |  "authProviderId": "12345-PID",
       |  "authProviderType": "Verify"
       |}
     """.stripMargin

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

  def stubGetUserDetails(userId: String): StubMapping = {
    val getUserUrl = s"/user-details/id/$userId"
    stubFor(get(urlMatching(getUserUrl))
      .willReturn(
        aResponse().
          withStatus(200).
          withBody(
            userDetails
          )
      )
    )
  }

  val emailResponseFromCr =
    """ {
      |  "address": "fudge@fromcomgreg.com",
      |  "type": "GG",
      |  "link-sent": false,
      |  "verified": false,
      |  "return-link-email-sent" : false
      |
      | }
    """.stripMargin

  "basicCompanyDetails" should {


    "call coho with a request that contains a session block and comp reg email is different to auth" in new Setup {

      stubSuccessfulLogin(userId = userId)
      stubAuthorisation(resp = Some(
        s"""
           |{
           |  "name": { "name": "name"},
           |  "email": "test@me.com",
           |  "externalId": "Ext-xxx"
           |}
         """.stripMargin))

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubGet(s"/company-registration/corporation-tax-registration/$regId/retrieve-email", 200, emailResponseFromCr)
      stubKeystore(SessionId, regId)
      await(repo.insertNavModel(regId, handOffNavModel))

      stubGetUserDetails(userId)

      val fResponse = buildClient("/basic-company-details").
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)
      val encryptedHandOffString = response.header(HeaderNames.LOCATION).get.split("request=").takeRight(1)(0)
      val decryptedHandoffJson = app.injector.instanceOf[JweCommon].decrypt[JsObject](encryptedHandOffString).get

      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/initial-coho-link")
      decryptedHandoffJson mustBe forwardPayloadJson
    }
  }

  "returnToAboutYou" should {

    "redirect to completion capacity if the payload is correct" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)

      val fResponse = buildClient(returnEncryptedRequest(app.injector.instanceOf[JweCommon].encrypt[JsObject](returnPayloadJson).get)).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)

      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/relationship-to-company")
    }

    "return a bad request if there is an incorrect request" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystore(SessionId, regId)

      val fResponse = buildClient(returnEncryptedRequest("malformed-encrypted-json")).
        withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck").
        get()

      val response = await(fResponse)

      response.status mustBe 400
    }
  }
}
