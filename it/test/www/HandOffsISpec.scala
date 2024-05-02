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

import test.fixtures.HandOffFixtures
import test.itutil.{IntegrationSpecBase, LoginStub}
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner

class HandOffsISpec extends IntegrationSpecBase with LoginStub with HandOffFixtures {

  def followRequest(path: String) = ws.url(s"http://localhost:$port$path").withFollowRedirects(false)

  val userId = "test-user-id"
  val regId = "12345"

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  val userDetails =
    s"""
       |{
       |  "name":"name",
       |  "email":"test@me.com",
       |  "affinityGroup" : "Organisation",
       |  "description" : "description",
       |  "lastName":"test",
       |  "dateOfBirth":"1980-06-30",
       |  "postCode":"ZZ11ZZ",
       |  "authProviderId": "12345-PID",
       |  "authProviderType": "Verify"
       |}
     """.stripMargin

  val footprintResponse =
    s"""
       |{
       |  "registration-id":"$regId",
       |  "created":true,
       |  "confirmation-reference":false,
       |  "payment-reference":false,
       |  "email":{
       |    "address":"some@email.com",
       |    "type":"test",
       |    "link-sent":true,
       |    "verified":true
       |  }
       |}
     """.stripMargin

  Seq(
    ("HO1b", "/return-to-about-you", HO1B_PAYLOAD),
    ("HO2", "/corporation-tax-details", HO2_PAYLOAD),
    ("HO3b", "/business-activities-back", HO3B_PAYLOAD),
    ("HO3-1", "/groups-handback", H03_1_PAYLOAD_FLAG),
    ("HO4", "/corporation-tax-summary", HO4_PAYLOAD),
    ("HO5b", "/return-to-corporation-tax-summary", HO5B_PAYLOAD)
  ).foreach { case (num, url, payload) =>
    s"GET $url when keystore has expired redirect to post sign in, set up keystore and redirect back to $num" in {
      Given("The user is authorised")

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)
      stubUserDetails(userId, userDetails)
      stubFootprint(200, footprintResponse)
      stubCorporationTaxRegistration(regId)

      And("Keystore has expired")
      stubKeystore(SessionId, regId, 404)

      When(s"A GET request is made to $url with a payload")
      val response = await(buildClient(s"$url?request=$payload")
        .withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId))
        .get())

      And("The request is redirected to /post-sign-in")
      response.status mustBe 303
      val redirect = response.header(HeaderNames.LOCATION).get
      redirect mustBe s"/register-your-company/post-sign-in?handOffID=$num&payload=$payload"

      And("A new keystore entry is created")
      stubKeystoreSave(SessionId, regId, 200)
      stubKeystore(SessionId, regId)


      Then(s"The request is redirected back to $url with the payload as a query string")
      val responseBack = await(followRequest(redirect)
        .withHttpHeaders(HeaderNames.COOKIE -> getSessionCookie(userId = userId))
        .get())

      responseBack.status mustBe 303
      responseBack.header(HeaderNames.LOCATION).get mustBe s"http://localhost:9970/register-your-company$url?request=$payload"
    }
  }
}