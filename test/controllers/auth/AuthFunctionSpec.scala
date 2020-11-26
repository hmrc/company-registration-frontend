/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.auth

import builders.AuthBuilder
import config.FrontendAppConfig
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.mvc.{Action, MessagesControllerComponents, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrievals, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector, AuthorisationException, Enrolments}
import uk.gov.hmrc.http.InternalServerException

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AuthFunctionSpec extends SCRSSpec with PayloadFixture with GuiceOneAppPerSuite with AuthBuilder with LoginFixture {

  class Setup {
    implicit val fr = FakeRequest()
    val authFunc = new AuthenticatedController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      override val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

      override def authConnector: AuthConnector = mockAuthConnector

      implicit val ec: ExecutionContext = global
    }

    def fudgeControllerActionctAuthorisedBasicCompanyDetails = Action.async {
      implicit request =>
        authFunc.ctAuthorisedBasicCompanyDetails { _ =>
          Future.successful(Results.Ok("foo"))
        }
    }

    def fudgeControllerActionctAuthorisedCompanyContact = Action.async {
      implicit request =>
        authFunc.ctAuthorisedCompanyContact { _ =>
          Future.successful(Results.Ok("foo"))
        }
    }

    def fudgeControllerActionctAuthorisedCompanyContactAmend = Action.async {
      implicit request =>
        authFunc.ctAuthorisedEmailCredsExtId { (a, b, c) =>
          Future.successful(Results.Ok("foo"))
        }
    }

    def fudgeControllerActionctAuthorisedPostSignIn = Action.async {
      implicit request =>
        authFunc.ctAuthorisedPostSignIn { _ =>
          Future.successful(Results.Ok("foo"))
        }
    }
  }


  "ctAuthorisedBasicCompanyDetails" must {
    val authDetails = new ~(new ~(Name(Some("myFirstName"), Some("myLastName")), Some("fakeEmail")), Some("extID"))
    "redirect to future passed in if auth returns correct deets" in new Setup {
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedBasicCompanyDetails, authDetails) {
        res =>
          val awaitedRes = await(res)
          awaitedRes.header.status shouldBe 200
          contentAsString(awaitedRes) shouldBe "foo"
      }
    }
    "redirect to no email page if no email is returned from auth" in new Setup {
      val authDetailsNoEmail = new ~(new ~(Name(Some("myFirstName"), Some("myLastName")), None), Some("extID"))
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedBasicCompanyDetails, authDetailsNoEmail) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 303
        awaitedRes.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createShow().url
      }
    }
    "return 500 if auth returns incorrect deets" in new Setup {
      val authDetailsInvalid = new ~(new ~(Name(Some("myFirstName"), Some("myLastName")), None), None)
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedBasicCompanyDetails, authDetailsInvalid) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 500
      }
    }
  }
  "ctAuthorisedCompanyContact" must {
    val authDetails = new ~(Name(Some("myFirstName"), Some("myLastName")), Some("fakeEmail"))
    "redirect to future passed in if auth returns correct deets" in new Setup {
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContact, authDetails) {
        res =>
          val awaitedRes = await(res)
          awaitedRes.header.status shouldBe 200
          contentAsString(awaitedRes) shouldBe "foo"
      }
    }
    "redirect to no email page if no email is returned from auth" in new Setup {
      val authDetailsNoEmail = new ~(Name(Some("myFirstName"), Some("myLastName")), None)
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContact, authDetailsNoEmail) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 303
        awaitedRes.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createShow().url
      }
    }
    "return 303 to no email page if auth returns incorrect deets" in new Setup {
      val authDetailsInvalid = new ~(Name(None, None), None)
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContact, authDetailsInvalid) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 303
        awaitedRes.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createShow().url
      }
    }
  }

  "ctAuthorisedCompanyContactAmend" must {
    val authDetails = new ~(
      new ~(
        new ~(
          Name(Some("myFirstName"), Some("myLastName")),
          Some("fakeEmail")
        ), Credentials("credID", "provID")
      ), Some("extID")
    )

    "redirect to future passed in if auth returns correct deets" in new Setup {
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContactAmend, authDetails) {
        res =>
          val awaitedRes = await(res)
          awaitedRes.header.status shouldBe 200
          contentAsString(awaitedRes) shouldBe "foo"
      }
    }
    "redirect to no email page if no email is returned from auth" in new Setup {
      val authDetailsNoEmail = new ~(
        new ~(
          new ~(
            Name(Some("myFirstName"), Some("myLastName")),
            None
          ), Credentials("credID", "provID")
        ), Some("extID")
      )
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContactAmend, authDetailsNoEmail) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 303
        awaitedRes.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createShow().url
      }
    }
    "return 500 if auth returns incorrect deets" in new Setup {
      val authDetailsInvalid = new ~(
        new ~(
          new ~(
            Name(Some("myFirstName"), Some("myLastName")),
            None
          ), Credentials("credID", "provID")
        ), None
      )
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedCompanyContactAmend, authDetailsInvalid) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 500
      }
    }
  }
  "ctAuthorisedPostSignIn" must {
    val authDetails = new ~(
      new ~(
        new ~(
          new ~(
            Some(AffinityGroup.Organisation),
            Enrolments(Set())
          ), Some("test")
        ), Some("test")
      ), Credentials("test", "test")
    )
    "redirect to future passed in if auth returns correct deets" in new Setup {
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedPostSignIn, authDetails) {
        res =>
          val awaitedRes = await(res)
          awaitedRes.header.status shouldBe 200
          contentAsString(awaitedRes) shouldBe "foo"
      }
    }
    "redirect to no email page if no email is returned from auth" in new Setup {
      val authDetailsNoEmail = new ~(
        new ~(
          new ~(
            new ~(
              Some(AffinityGroup.Organisation),
              Enrolments(Set())
            ), None
          ), Some("test")
        ), Credentials("test", "test")
      )
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedPostSignIn, authDetailsNoEmail) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 303
        awaitedRes.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createShow().url
      }
    }
    "return 500 if auth returns incorrect deets" in new Setup {
      val authDetailsInvalid = new ~(
        new ~(
          new ~(
            new ~(
              None,
              Enrolments(Set())
            ), None
          ), Some("foo")
        ), Credentials("test", "test")
      )
      showWithAuthorisedUserRetrieval(fudgeControllerActionctAuthorisedPostSignIn, authDetailsInvalid) { res =>
        val awaitedRes = await(res)
        awaitedRes.header.status shouldBe 500
      }
    }
  }
  "scpVerifiedEmail" should {

    "Return a true if the email has been verified e" in new Setup {

      mockAuthorisedUser(Future.successful(Some(true)))
      val res = authFunc.scpVerifiedEmail
      val awaitedFuture: Boolean = await(res)

      awaitedFuture shouldBe true
    }


    "Return a false if the email has not been verified" in new Setup {

      mockAuthorisedUser(Future.successful(Some(false)))
      val res = authFunc.scpVerifiedEmail
      val awaitedFuture: Boolean = await(res)

      awaitedFuture shouldBe false
    }

    "Return a false if the email is missing and SCP feature flag is true" in new Setup {

      mockAuthorisedUser(Future.successful(None))
      val res = authFunc.scpVerifiedEmail
      val awaitedFuture: Boolean = await(res)

      awaitedFuture shouldBe false
    }

    "Return a false if an error is returned from the SCP verified email function and SCP feature flag is true" in new Setup {

      mockAuthorisedUser(Future.failed(new InternalServerException("error")))
      val res = authFunc.scpVerifiedEmail
      val awaitedFuture: Boolean = await(res)

      awaitedFuture shouldBe false
    }
  }
  "authErrorHandling" should {
    Map("case 1" -> "InsufficientConfidenceLevel",
      "case 2" -> "UnsupportedAffinityGroup",
      "case 3" -> "UnsupportedCredentialRole",
      "case 4" -> "UnsupportedAuthProvider",
      "case 9" -> "IncorrectCredentialStrength",
      "case 10" -> "InsufficientEnrolments").foreach {
      tst =>
        val (test, ex) = tst
        s"$test redirect to incorrect account type page if auth returns $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(authFunc.authErrorHandling()).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
        }
    }
    "internal server error if auth returns non matching string exception" in new Setup {
      Try(throw AuthorisationException.fromString("fudge"))
        .recover(authFunc.authErrorHandling()).get shouldBe Results.InternalServerError
    }
    Map("case 5" -> "BearerTokenExpired",
      "case 6" -> "MissingBearerToken",
      "case 7" -> "InvalidBearerToken",
      "case 8" -> "SessionRecordNotFound").foreach {
      tst =>
        val (test, ex) = tst
        s"$test redirect to sign in  with postsignin as continue url for $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(authFunc.authErrorHandling()).get.header.headers(HeaderNames.LOCATION) shouldBe "http://localhost:9553/bas-gateway/sign-in?continue=http%3A%2F%2Flocalhost%3A9970%2Fregister-your-company%2Fpost-sign-in&origin=company-registration-frontend"
        }
    }
  }
  "authErrorHandlingIncomplete" should {
    Map("case 1" -> "InsufficientConfidenceLevel",
      "case 2" -> "UnsupportedAffinityGroup",
      "case 3" -> "UnsupportedCredentialRole",
      "case 4" -> "UnsupportedAuthProvider",
      "case 9" -> "IncorrectCredentialStrength",
      "case 10" -> "InsufficientEnrolments").foreach {
      tst =>
        val (test, ex) = tst
        s"$test redirect to incorrect account type page if auth returns $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(authFunc.authErrorHandlingIncomplete).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
        }
    }
    "internal server error if auth returns non matching string exception" in new Setup {
      Try(throw AuthorisationException.fromString("fudge"))
        .recover(authFunc.authErrorHandlingIncomplete).get shouldBe Results.InternalServerError
    }
    Map("case 5" -> "BearerTokenExpired",
      "case 6" -> "MissingBearerToken",
      "case 7" -> "InvalidBearerToken",
      "case 8" -> "SessionRecordNotFound").foreach {
      tst =>
        val (test, ex) = tst
        s"$test redirect to sign in  with postsignin as continue url for $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(authFunc.authErrorHandlingIncomplete).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.reg.routes.IncompleteRegistrationController.show().url
        }
    }
  }


  "ctAuthorised" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = authFunc.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = authFunc.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl)
    }

    "redirect to Incorrect account type server error if an auth error occurs" in new Setup {
      mockAuthFailure()
      val result = authFunc.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
    }
  }

  "ctAuthorisedHandoff" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = authFunc.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in with a continue to a handoff if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = authFunc.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl("h", "p"))
    }
  }
  "ctAuthorisedOptStr" should {
    "return Ok when auth returns a retrieval the user was expecting" in new Setup {
      mockAuthorisedUser(Future.successful(Some("foo")))
      val result = authFunc.ctAuthorisedOptStr(Retrievals.externalId)((s: String) => Future.successful(Results.Ok(s"$s")))
      val response = await(result)
      response.header.status shouldBe 200
      contentAsString(response) shouldBe "foo"
    }
    "return an internal server error if auth does not return something expected in the retrieval passed in" in new Setup {
      mockAuthorisedUser(Future.successful(None))
      val result = authFunc.ctAuthorisedOptStr(Retrievals.externalId)((s: String) => Future.successful(Results.Ok(s"$s")))
      val response = await(result)
      response.header.status shouldBe 500
      contentAsString(response) shouldBe ""
    }
  }
}