/*
 * Copyright 2019 HM Revenue & Customs
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

package utils

import builders.AuthBuilder
import controllers.auth.AuthFunction
import fixtures.LoginFixture
import helpers.SCRSSpec
import play.api.http.HeaderNames
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future
import scala.util.Try

class AuthFunctionSpec extends SCRSSpec with AuthBuilder with WithFakeApplication with LoginFixture {

  override implicit val hc = HeaderCarrier()
  implicit val fr = FakeRequest()


  trait Setup {
    val support = new AuthFunction {
      override def authConnector = mockAuthConnector
    }
  }

  "authErrorHandling" should {
    Map("case 1" -> "InsufficientConfidenceLevel",
    "case 2" -> "UnsupportedAffinityGroup",
    "case 3" -> "UnsupportedCredentialRole",
    "case 4" -> "UnsupportedAuthProvider",
    "case 9" -> "IncorrectCredentialStrength" ,
    "case 10" -> "InsufficientEnrolments" ).foreach{
      tst =>
        val (test,ex) = tst
        s"$test redirect to incorrect account type page if auth returns $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(support.authErrorHandling()).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
        }
    }
    "internal server error if auth returns non matching string exception" in new Setup {
      Try(throw AuthorisationException.fromString("fudge"))
        .recover(support.authErrorHandling()).get shouldBe Results.InternalServerError
    }
    Map("case 5" -> "BearerTokenExpired",
      "case 6" -> "MissingBearerToken",
      "case 7" -> "InvalidBearerToken",
      "case 8" -> "SessionRecordNotFound").foreach{
      tst =>
        val (test, ex) = tst
        s"$test redirect to sign in  with postsignin as continue url for $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(support.authErrorHandling()).get.header.headers(HeaderNames.LOCATION) shouldBe "http://localhost:9025/gg/sign-in?accountType=organisation&continue=http%3A%2F%2Flocalhost%3A9970%2Fregister-your-company%2Fpost-sign-in&origin=company-registration-frontend"
        }
    }
  }
  "authErrorHandlingIncomplete" should {
    Map("case 1" -> "InsufficientConfidenceLevel",
      "case 2" -> "UnsupportedAffinityGroup",
      "case 3" -> "UnsupportedCredentialRole",
      "case 4" -> "UnsupportedAuthProvider",
      "case 9" -> "IncorrectCredentialStrength" ,
      "case 10" -> "InsufficientEnrolments" ).foreach{
      tst =>
        val (test,ex) = tst
        s"$test redirect to incorrect account type page if auth returns $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(support.authErrorHandlingIncomplete).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
        }
    }
    "internal server error if auth returns non matching string exception" in new Setup {
      Try(throw AuthorisationException.fromString("fudge"))
        .recover(support.authErrorHandlingIncomplete).get shouldBe Results.InternalServerError
    }
    Map("case 5" -> "BearerTokenExpired",
      "case 6" -> "MissingBearerToken",
      "case 7" -> "InvalidBearerToken",
      "case 8" -> "SessionRecordNotFound").foreach{
      tst =>
        val (test, ex) = tst
        s"$test redirect to sign in  with postsignin as continue url for $ex" in new Setup {
          Try(throw AuthorisationException.fromString(ex))
            .recover(support.authErrorHandlingIncomplete).get.header.headers(HeaderNames.LOCATION) shouldBe controllers.reg.routes.IncompleteRegistrationController.show().url
        }
    }
  }


  "ctAuthorised" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl)
    }

    "redirect to Incorrect account type server error if an auth error occurs" in new Setup {
      mockAuthFailure()
      val result = support.ctAuthorised(Results.Ok)

      val response = await(result)
      response.header.headers(HeaderNames.LOCATION) shouldBe controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow().url
    }
  }

  "ctAuthorisedHandoff" should {
    "redirect to OK if the person is signed in" in new Setup {
      mockAuthorisedUser({})
      val result = support.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 200
    }

    "return to post-sign-in with a continue to a handoff if the person is not signed in" in new Setup {
      mockUnauthorisedUser()
      val result = support.ctAuthorisedHandoff("h", "p")(Results.Ok)

      val response = await(result)
      response.header.status shouldBe 303
      redirectLocation(result) shouldBe Some(authUrl("h", "p"))
    }
  }
  "ctAuthorisedOptStr" should {
    "return Ok when auth returns a retrieval the user was expecting" in new Setup {
      mockAuthorisedUser(Future.successful(Some("foo")))
      val result = support.ctAuthorisedOptStr(Retrievals.externalId)((s:String) => Future.successful(Results.Ok(s"$s")))
      val response = await(result)
      response.header.status shouldBe 200
      contentAsString(response) shouldBe "foo"
    }
    "return an internal server error if auth does not return something expected in the retrieval passed in" in new Setup {
      mockAuthorisedUser(Future.successful(None))
      val result = support.ctAuthorisedOptStr(Retrievals.externalId)((s:String) => Future.successful(Results.Ok(s"$s")))
      val response = await(result)
      response.header.status shouldBe 500
      contentAsString(response) shouldBe ""
    }
  }
  val authDetails = new ~(
    Name(Some("myFirstName"), Some("myLastName")),
    Some("fakeEmail")
  )
  val authMissing = new ~(
    Name(Some("myFirstName"), Some("myLastName")),
    None
  )
  val authDetailsAmend = new ~(
    new ~(
      new ~(
        Name(Some("myFirstName"), Some("myLastName")),
        Some("fakeEmail")
      ), Credentials("credID", "provID")
    ), Some("extID")
  )
  "ctAuthorisedCompanyContact" should {
    "return Ok when all required components are returned from auth" in new Setup {
      mockAuthorisedUser(Future.successful(authDetails))
      val result = support.ctAuthorisedCompanyContact((s:String) => Future.successful(Results.Ok(s"$s")))
      val response = await(result)
      response.header.status shouldBe 200
      contentAsString(response) shouldBe "fakeEmail"

    }
    "return internal server error When Not all the components are returned from auth" in new Setup {
      mockAuthorisedUser(Future.successful(authMissing))
      val result = support.ctAuthorisedCompanyContact((s:String) => Future.successful(Results.Ok(s"$s")))
      intercept[Exception](await(result))
    }
  }
  "ctAuthorisedCompanyContactAmend" should {
    "return Ok when all required components are returned from auth" in new Setup {
      mockAuthorisedUser(Future.successful(authDetailsAmend))
      val result = support.ctAuthorisedCompanyContactAmend(
        (s:String, c:Credentials, ss:String) => Future.successful(Results.Ok(s"$s$c$ss")))
      val response = await(result)
      response.header.status shouldBe 200
      contentAsString(response) shouldBe "fakeEmailCredentials(credID,provID)extID"


    }
    "return internal server error When Not all the components are returned from auth" in new Setup {
      mockAuthorisedUser(Future.successful(authDetails))
      val result = support.ctAuthorisedCompanyContactAmend(
        (s:String, c:Credentials, ss:String) => Future.successful(Results.Ok(s"$s$c$ss")))
     intercept[Exception](await(result))
    }
  }
}
