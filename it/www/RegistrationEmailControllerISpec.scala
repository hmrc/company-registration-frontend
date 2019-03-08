

package www

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import controllers.reg.routes
import itutil.{FakeAppConfig, IntegrationSpecBase, LoginStub}
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeApplication
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

class RegistrationEmailControllerISpec extends IntegrationSpecBase with LoginStub with FakeAppConfig with MockitoSugar  {

  override implicit lazy val app = FakeApplication(additionalConfiguration = fakeConfig())
  val mockSCRSFeatureSwitches = mock[SCRSFeatureSwitches]

  class Setup {
    val userId = "test-user-id"
    val regId = "12345"
    val csrfToken = () => UUID.randomUUID().toString
    val sessionCookie = () => getSessionCookie(Map("csrfToken" -> csrfToken()), userId)
    val scrsFeatureSwitches = mockSCRSFeatureSwitches
    val featureSwitchTrue = BooleanFeatureSwitch("sCPEnabled", true)
    val featureSwitchFalse = BooleanFeatureSwitch("sCPEnabled", false)

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

  s"${controllers.reg.routes.RegistrationEmailController.show().url}" should {
    "return 200 when user is logged in and has a keystore entry for regId, email from auth should be populated on the page. user has not been to page before" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show().url)
      .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
      .get())

      res.status shouldBe 200
      val doc = Jsoup.parse(res.body)
      doc.body().toString.contains("test@test.com") shouldBe true
      doc.getElementById("registrationEmail-currentemail").attr("checked") shouldBe ""
      doc.getElementById("registrationEmail-differentemail").attr("checked") shouldBe "checked"
      doc.getElementById("DifferentEmail").`val` shouldBe "sausage"
    }
    "return 200 when user is logged in and has a keystore entry for regId, email from auth should be populated on the page user has also been to page before" in new Setup {

      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe 200
      val doc = Jsoup.parse(res.body)
      doc.body().toString.contains("test@test.com") shouldBe true
      doc.getElementById("registrationEmail-currentemail").attr("checked") shouldBe ""
      doc.getElementById("registrationEmail-differentemail").attr("checked") shouldBe ""
      doc.getElementById("DifferentEmail").`val` shouldBe ""
    }
    "redirect to post sign in if verified flag is already true" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.SignInOutController.postSignIn().url
    }
  }
  s"${controllers.reg.routes.RegistrationEmailController.submit().url}" should {
    "return 303 to email verification when user submits valid data of currentEmail, but email verify returns false" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
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

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
        "csrfToken" -> Seq("xxx-ignored-xxx"),
        "registrationEmail" -> Seq("currentEmail")
      )))

      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.verification.routes.EmailVerificationController.verifyShow().url
    }
    "return 303 to completion capacity when user submits valid data of currentEmail & email verify returns true" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
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

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("currentEmail")
        )))

      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.CompletionCapacityController.show().url
    }
    "return 303 to email confirmation controller when user enters different email" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)
      stubKeystoreCache(SessionId,"RegEmail",200)
      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("differentEmail"),
          "DifferentEmail" -> Seq("foobar@wizz.com")
        )))


      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe routes.RegistrationEmailConfirmationController.show().url
    }
    "return 303 to post sign in if user is already verified in CR backend" in new Setup {
      stubAuthorisation()
      stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar")))
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
      stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
      stubKeystoreGetWithJson(SessionId, regId, 200, data)

      val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
        .post(Map(
          "csrfToken" -> Seq("xxx-ignored-xxx"),
          "registrationEmail" -> Seq("differentEmail"),
          "DifferentEmail" -> Seq("foobar@wizz.com")
        )))

      res.status shouldBe 303
      res.header(HeaderNames.LOCATION).get shouldBe routes.SignInOutController.postSignIn().url
    }


      "return 303 to completion capacity if SCP Enabled and email is SCP Verified" in new Setup {
          setupFeatures(scpEnabled = true)
          stubAuthorisation()
          stubSuccessfulLogin(userId = userId, otherParamsForAuth = Some(Json.obj("name" -> "foobar", "emailVerified" -> true)))
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
        stubGet("/company-registration/corporation-tax-registration/test/retrieve-email",200, emailResponseFromCr)
        stubKeystoreGetWithJson(SessionId, regId, 200, data)

          val res = await(buildClient(controllers.reg.routes.RegistrationEmailController.submit().url)
            .withHeaders(HeaderNames.COOKIE -> sessionCookie(), "Csrf-Token" -> "nocheck")
          .post(Map(
              "csrfToken" -> Seq("xxx-ignored-xxx"),
              "registrationEmail" -> Seq("currentEmail")
                )))

          res.status shouldBe 303
        res.header(HeaderNames.LOCATION).get shouldBe controllers.reg.routes.CompletionCapacityController.show().url
        }
  }

}