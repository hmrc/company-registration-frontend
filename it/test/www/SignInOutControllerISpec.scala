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

import java.time.LocalDate
import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import test.itutil._
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner

class SignInOutControllerISpec extends IntegrationSpecBase with LoginStub with RequestsFinder {

  val userId = "/bar/foo"
  val testKeystoreKey = "testKey"
  val regId = "regId5"
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

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

  val throttleWithAnSCRSValidatedEmail =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : true,
       |  "email" : {
       |      "address": "foo@bar.wibble",
       |      "type": "GG",
       |      "link-sent": true,
       |      "verified": true
       |  }
       |}
       """.stripMargin

  val throttleWithOutAnSCRSValidatedEmail =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : true,
       |  "email" : {
       |      "address": "foo@bar.wibble",
       |      "type": "GG",
       |      "link-sent": false,
       |      "verified": false
       |  }
       |}
       """.stripMargin

  val throttleWithOutAnSCRSValidatedEmailButLinkSent =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : true,
       |  "email" : {
       |      "address": "foo@bar.wibble",
       |      "type": "GG",
       |      "link-sent": true,
       |      "verified": false
       |  }
       |}
       """.stripMargin

  val throttleWithNoEmailAddressInEmailBlock =
    s"""
       |{
       |  "registration-id" : "$regId",
       |  "created" : true,
       |  "confirmation-reference" : true,
       |  "payment-reference" : true,
       |  "email" : {
       |      "address": "",
       |      "type": "GG",
       |      "link-sent": false,
       |      "verified": false
       |  }
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

  def stubVerifyEmail(vEmail: String, vStatus: Int): StubMapping = {
    val postUserUrl = s"/checkVerifiedEmailURL"
    stubFor(post(urlMatching(postUserUrl))
      .willReturn(
        aResponse().
          withStatus(vStatus).
          withBody(
            s"""{
               |"email": "$vEmail"
               |}""".stripMargin
          )
      )
    )
  }

  "Sign In" should {

    "redirect to ho1 if status is held and no payment reference is present" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "held")
      stubGet("/company-registration/throttle/check-user-access", 200, heldAndNoPaymeentRefsThrottle)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/basic-company-details")
    }

    "redirect to ho1 if status is locked" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "locked")
      stubGet("/company-registration/throttle/check-user-access", 200, lockedThrottle)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/basic-company-details")
    }
    "redirect to completion capacity if status is draft and we have an SCRS verified email" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "draft")
      stubGet("/company-registration/throttle/check-user-access", 200, throttleWithAnSCRSValidatedEmail)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/relationship-to-company")
    }

    "redirect to the email verification screen if status is draft and we don't have an SCRS verified email" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "draft")
      stubGet("/company-registration/throttle/check-user-access", 200, throttleWithOutAnSCRSValidatedEmail)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/registration-email")
    }

    "redirect to the email verification screen if status is draft and we don't have an SCRS verified email but we have sent an email link and the email is still not verified" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "draft")
      stubGet("/company-registration/throttle/check-user-access", 200, throttleWithOutAnSCRSValidatedEmailButLinkSent)

      stubPut("/company-registration/corporation-tax-registration/regId5/update-email", 200,
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false
          | }
                                                                                            """.stripMargin)

      stubGetUserDetails(userId)

      stubVerifyEmail("foo@bar.wibble", 404)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/registration-email")
    }

    "redirect to the completion capacity screen if status is draft and we don't have an SCRS verified email but we have sent an email link and the email is now verified" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "draft")
      stubGet("/company-registration/throttle/check-user-access", 200, throttleWithOutAnSCRSValidatedEmailButLinkSent)

      stubPut("/company-registration/corporation-tax-registration/regId5/update-email", 200,
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false
          | }
                                                                                            """.stripMargin)

      stubGetUserDetails(userId)

      stubVerifyEmail("foo@bar.wibble", 200)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/relationship-to-company")
    }

    "redirect to the enter-your-details if status is draft and we don't have an SCRS verified email and there is no email address for the account" in {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId)

      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

      stubKeystoreCache(SessionId, "registrationID")
      stubKeystoreGet(SessionId, regId)

      stubGetRegistrationStatus(regId, "draft")
      stubGet("/company-registration/throttle/check-user-access", 200, throttleWithNoEmailAddressInEmailBlock)

      stubGetUserDetails(userId)

      val fResponse = buildClient("/post-sign-in").
        withHttpHeaders(HeaderNames.COOKIE -> sessionCookie).
        get()

      val response = await(fResponse)
      response.status mustBe 303
      response.header(HeaderNames.LOCATION).get must include("/register-your-company/enter-your-details")
    }


  }

  "Sign Out" should {
    "Return a redirect to Bas Gateway sign out" in {
      stubAuthorisation()

      val response = await(buildClient("/sign-out").get())

      response.status mustBe 303

      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo mustBe defined
      redirectTo map { r =>
        r must include("/bas-gateway/sign-out")
      }
    }

    "Return a redirect to Bas Gateway sign out with relative continue URL" in {
      stubAuthorisation()

      val continueURL = "/foo/bar"
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status mustBe 303
      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo mustBe defined
      redirectTo map { r =>
        r must include("/bas-gateway/sign-out")
        r must include(encodeURL(continueURL))
      }
    }

    "Return a redirect to Bas Gateway sign out with absolute continue URL" in {
      stubAuthorisation()

      val continueURL = "http://foo.gov.uk/foo/bar"
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status mustBe 303
      val redirectTo = response.header(HeaderNames.LOCATION)

      redirectTo mustBe defined
      redirectTo map { r =>
        r must include("/bas-gateway/sign-out")
        r must include(encodeURL(continueURL))
      }
    }

    "Return a bad request if URL isn't valid" in {
      stubAuthorisation()

      val continueURL = "//foo.gov.uk/foo/bar"
      val response = await(buildClient(s"/sign-out?continueUrl=${encodeURL(continueURL)}").get())

      response.status mustBe 400
    }
  }

  "Renew session" should {
    "update keystore and return an image with a status of 200 verifying the PUT to keystore" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubAuthorisation()
      stubKeystoreCache(SessionId, "lastActionTimestamp")

      val response = await(buildClient("/renew-session")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())

      response.status mustBe 200
      response.header("Content-Type") mustBe Some("image/jpeg")
      response.header("Content-Disposition") mustBe Some("""inline; filename="renewSession.jpg"""")
      response.header(HeaderNames.CACHE_CONTROL) mustBe Some("no-cache,no-store,max-age=0")

      val request = getPUTRequestJsonBody(s"/keystore/company-registration-frontend/$SessionId/data/lastActionTimestamp")
      request.as[String] mustBe LocalDate.now.toString
    }

    "throw an exception when keystore update fails and return a 500" in {
      val csrfToken = UUID.randomUUID().toString
      val sessionCookie = getSessionCookie(Map("csrfToken" -> csrfToken), userId)
      stubAuthorisation()
      stubKeystoreCache(SessionId, "lastActionTimestamp", status = 500)
      val response = await(buildClient("/renew-session")
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie)
        .get())

      response.status mustBe 500
      response.header(HeaderNames.CACHE_CONTROL) mustBe Some("no-cache,no-store,max-age=0")
    }
  }
}