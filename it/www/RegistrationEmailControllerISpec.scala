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
import controllers.reg.routes
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.crypto.DefaultCookieSigner
import play.api.libs.json.{JsObject, Json}

class RegistrationEmailControllerISpec extends IntegrationSpecBase with LoginStub with MockitoSugar with RequestsFinder {

  class Setup {
    val userId = "test-user-id"
    val regId = "12345"
    val csrfToken = () => UUID.randomUUID().toString
    val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
    val nameJson = Json.parse(
      """{ "name": {"name": "foo", "lastName": "bar"}}""".stripMargin).as[JsObject]
    val nameAndCredId = Json.obj("externalId" -> "fooBarWizz1") ++ nameJson

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
  }

  lazy val defaultCookieSigner: DefaultCookieSigner = app.injector.instanceOf[DefaultCookieSigner]

  s"${controllers.reg.routes.RegistrationEmailController.show.url}" should {
    "return 200 when user is logged in and has a keystore entry for regId, email from auth should be populated on the page. user has not been to page before" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> Json.obj("name" -> "testName"))))
      stubKeystore(SessionId, regId)
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
          |"RegEmail" : {
          | "currentEmail": "differentEmail",
          | "differentEmail": "sausage"
          |},
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      res.status mustBe 200
      val doc = Jsoup.parse(res.body)
      doc.body().toString.contains("test@test.com") mustBe true
      doc.getElementById("differentEmail").`val` mustBe "differentEmail"
    }
    "return 200 when user is logged in and has a keystore entry for regId, email from auth should be populated on the page user has also been to page before" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> Json.obj("name" -> "testName"))))
      stubKeystore(SessionId, regId)
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
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      res.status mustBe 200
      val doc = Jsoup.parse(res.body)
      doc.body().toString.contains("test@test.com") mustBe true
      doc.getElementById("registrationEmail").attr("checked") mustBe ""
      doc.getElementById("differentEmail").attr("checked") mustBe ""
    }
    "redirect to post sign in if verified flag is already true" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> Json.obj("name" -> "testName"))))
      val emailResponseFromCr =
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": false,
          |  "verified": true,
          |  "return-link-email-sent" : false
          |
          | }
        """.stripMargin
      val data: String =
        """
          |{
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe controllers.reg.routes.SignInOutController.postSignIn().url
    }
  }
  s"${controllers.reg.routes.RegistrationEmailController.submit.url}" should {
    "return 303 to email verification when user submits valid data of currentEmail, but email verify returns false" in new Setup {
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
      val data: String =
        """
          |{
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      stubVerifyEmail(201)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": false
          | }
        """.stripMargin)

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("currentEmail")
        )))

      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe controllers.verification.routes.EmailVerificationController.verifyShow.url
    }
    "return 303 to completion capacity when user submits valid data of currentEmail & email verify returns true" in new Setup {
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
      val data: String =
        """
          |{
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      stubVerifyEmail(409)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": true,
          |  "verified": true
          | }
        """.stripMargin)

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("currentEmail")
        )))
      val audit = Json.parse(getRequestBody("post", "/write/audit")).as[JsObject] \ "detail"
      audit.get mustBe Json.parse("""{"externalUserId":"fooBarWizz1","authProviderId":"12345-credId","journeyId":"test","emailAddress":"test@test.com","isVerifiedEmailAddress":true,"previouslyVerified":true}""")

      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
    }
    "return 303 to email confirmation controller when user enters different email" in new Setup {
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
      val data: String =
        """
          |{
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      stubKeystoreCache(SessionId, "RegEmail", 200)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("differentEmail"),
          "DifferentEmail" -> Seq("foobar@wizz.com")
        )))


      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe routes.RegistrationEmailConfirmationController.show.url
    }
    "return 303 to post sign in if user is already verified in CR backend" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId))
      val emailResponseFromCr =
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "GG",
          |  "link-sent": false,
          |  "verified": true,
          |  "return-link-email-sent" : false
          |
          | }
        """.stripMargin
      val data: String =
        """
          |{
          | "registrationID" : "test"
          |}
        """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("differentEmail"),
          "DifferentEmail" -> Seq("foobar@wizz.com")
        )))

      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe routes.SignInOutController.postSignIn().url
    }


    "return 303 to completion capacity if SCP Verified" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(nameAndCredId.deepMerge(Json.obj("emailVerified" -> true))))
      stubKeystore(SessionId, regId)
      stubPut("/company-registration/corporation-tax-registration/test/update-email", 200,
        """ {
          |  "address": "foo@bar.wibble",
          |  "type": "SCP",
          |  "link-sent": false,
          |  "verified": true,
          |  "return-link-email-sent" : false
          | }
        """.stripMargin)

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
          | "registrationID" : "test"
          |}
      """.stripMargin
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email", 200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit.url)
        .withHttpHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("currentEmail")
        )))

      res.status mustBe 303
      res.header(HeaderNames.LOCATION).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
    }
  }
}