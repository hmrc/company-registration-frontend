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

import java.time.LocalDate
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil._
import org.jsoup.Jsoup
import play.api.http.HeaderNames
import play.api.test.FakeApplication

class SignInOutControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with RequestsFinder {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig("microservice.services.JWE.key" -> testkey))

  val userId = "/bar/foo"
  val testKeystoreKey = "testKey"
  val regId = "regId5"

  val heldAndNoPaymeentRefsThrottle =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : false
       |}
     """.stripMargin

  val lockedThrottle =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : true
       |}
     """.stripMargin


  val userDetails =
    s"""
      |{
      |  "name":"name",
      |  "email":"test@me.com",
      |  "affinityGroup" : "Organisation",
      |  "description" : "description",
      |  "lastName":"test",
      |  "dateOfBirth":"1980-06-30",
      |  "postCode":"NW94HD",
      |  "authProviderId": "12345-PID",
      |  "authProviderType": "Verify"
      |}
     """.stripMargin

  def encodeURL(url: String) = java.net.URLEncoder.encode(url, "UTF-8")

  def stubKeystoreCache(sessionId: String, key: String, status: Int = 200) = {
    stubFor(put(urlMatching(s"/keystore/company-registration-frontend/$sessionId/data/$key"))
      .willReturn(
        aResponse()
          .withStatus(status).
          withBody(
            s"""{
               |"id": "$sessionId",
               |"data": {}
               |}""".stripMargin
          )
      )
    )
  }

  def stubKeystoreGet(session: String, regId: String): StubMapping = {
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

  def stubGetRegistrationStatus(regId: String, status: String) = {
    stubFor(get(urlMatching(s"/company-registration/corporation-tax-registration/$regId/corporation-tax-registration"))
      .willReturn(
        aResponse()
          .withStatus(200).
          withBody(
            s"""{
               |"status": "$status"
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

  "Sign In" should  {

    "redirect to ho1 if status is held and no payment reference is present" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "held")
      stubGet("/company-registration/throttle/check-user-access",200, heldAndNoPaymeentRefsThrottle)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/basic-company-details")
    }

    "redirect to ho1 if status is locked" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "locked")
      stubGet("/company-registration/throttle/check-user-access",200, lockedThrottle)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/register-your-company/basic-company-details")
    }
  }

  "Sign Out" should {
    "Return a redirect to GG sign out" in {
      val response = await(buildClient("/sign-out").get())

      response.status shouldBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo shouldBe defined
      redirectTo map { r =>
        r should include("/gg/sign-out")
      }
    }

    "Return a redirect to GG sign out with relative continue URL" in {
      val continueURL = "/foo/bar"
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

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
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

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
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status shouldBe 400
      val document = Jsoup.parse(response.body)
      document.title() shouldBe "Bad request - 400"
      document.select("h1").text should include("Bad request")
    }
  }

  "Renew session" should {
    "update keystore and return an image with a status of 200 verifying the PUT to keystore" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubAuthorisation()
      stubKeystoreCache(SessionId, "lastActionTimestamp")

      val response = await(buildClient("/renew-session")
        .withHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())

      response.status shouldBe 200
      response.header("Content-Type") shouldBe Some("image/jpeg")
      response.header("Content-Disposition") shouldBe Some("""inline; filename="renewSession.jpg"; filename*=utf-8''renewSession.jpg""")

      val request = getPUTRequestJsonBody(s"/keystore/company-registration-frontend/$SessionId/data/lastActionTimestamp")
      request.as[String] shouldBe LocalDate.now.toString
    }

    "throw an exception when keystore update fails and return a 500" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubAuthorisation()
      stubKeystoreCache(SessionId, "lastActionTimestamp",status = 500)
      val response = await(buildClient("/renew-session")
        .withHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())

      response.status shouldBe 500
    }
  }
}