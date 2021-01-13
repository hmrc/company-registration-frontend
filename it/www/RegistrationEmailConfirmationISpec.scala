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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}


class RegistrationEmailConfirmationISpec extends IntegrationSpecBase with LoginStub with RequestsFinder {

  val userId = "/bar/foo"
  val csrfToken = () => UUID.randomUUID().toString
  val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  def stubVerifyEmail(vStatus: Int): StubMapping = {
    val postUserUrl = s"/sendVerificationEmailURL"
    stubFor(post(urlMatching(postUserUrl))
      .willReturn(
        aResponse().
          withStatus(vStatus).
          withBody(
            s"""{
               |}""".stripMargin
          )
      )
    )
  }

  s"${controllers.reg.routes.RegistrationEmailConfirmationController.show().url}" should {
    "GET for show should return 200" in {
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
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        get()
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 200
    }
  }
  val nameJson = Json.parse(
    """{ "name": {"name": "foo", "lastName": "bar"}}""".stripMargin).as[JsObject]
  val testExternalId = "testId"
  val nameAndCredId = Json.obj("externalId" -> testExternalId) ++ nameJson

  s"${controllers.reg.routes.RegistrationEmailConfirmationController.submit().url}" should {
    "POST for submit should return 303 and redirect to Completion Capacity if YES is selected AND email is already verified" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubVerifyEmail(vStatus = 409)
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo",
          |  "type": "GG",
          |  "link-sent": false,
          |  "verified": true
          | }
        """.stripMargin)
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("true")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 303
      awaitedFuture.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/relationship-to-company")
      val audit = (Json.parse(
        findAll(
          postRequestedFor(urlMatching("/write/audit")).withRequestBody(containing(testExternalId))
        ).get(0).getBodyAsString
      ).as[JsObject] \ "detail").toOption
      audit shouldBe Some(Json.parse("""{"externalUserId":"testId","authProviderId":"12345-credId","journeyId":"test","emailAddress":"foo","isVerifiedEmailAddress":true,"previouslyVerified":true}"""))
    }

    "POST for submit should return 303 and redirect to Email verification if YES is selected AND email is NOT verified" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubVerifyEmail(vStatus = 201)
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false
          | }
        """.stripMargin)
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("true")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 303
      awaitedFuture.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/sent-an-email")
    }

    "POST for submit should return 303 and redirect to Email verification if YES is selected AND email is NOT verified, but link was sent prior" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      stubVerifyEmail(vStatus = 201)
      val emailResponseFromCr =
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false,
          |  "return-link-email-sent" : false
          |
          | }
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false
          | }
        """.stripMargin)
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("true")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 303
      awaitedFuture.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/sent-an-email")
    }

    "POST for submit should return 303 and redirect to Registration Email if NO is selected" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo",
          |  "type": "GG",
          |  "link-sent": false,
          |  "verified": false
          | }
        """.stripMargin)
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("false")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 303
      awaitedFuture.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/registration-email")
    }

    "POST for submit should return 303 and redirect to Post Sign In if no data is available from the previous page" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      val data: String =
        """{
          | "registrationID" : "test"}""".stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("false")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 303
      awaitedFuture.header(HeaderNames.LOCATION) shouldBe Some("/register-your-company/post-sign-in")
    }

    "POST for submit should return 400 if bad data is submitted" in {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      val data: String =
        """
          |{
          | "RegEmail" : {
          |   "currentEmail" : "foo",
          |   "differentEmail" : "foo"
          | },
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubKeystoreGetWithJson(SessionId, "5", 200, data)
      stubKeystoreSave(SessionId, "5", 200)
      val fResponse = buildClient("/companies-house-email-confirm").
        withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck").
        post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "confirmRegistrationEmail" -> Seq("fffff")
        ))
      val awaitedFuture = await(fResponse)
      awaitedFuture.status shouldBe 400
    }
  }
}