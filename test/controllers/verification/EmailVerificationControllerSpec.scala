/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.verification

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import builders.AuthBuilder
import config.AppConfig
import helpers.SCRSSpec
import mocks.{CompanyRegistrationConnectorMock, KeystoreMock, SCRSMocks}
import models.Email
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import views.BaseSelectors

import scala.concurrent.ExecutionContext.Implicits.global
import views.html.verification.{CreateGGWAccount, CreateNewGGWAccount, createNewAccount, verifyYourEmail}


class EmailVerificationControllerSpec extends SCRSSpec with CompanyRegistrationConnectorMock with MockitoSugar with SCRSMocks
  with GuiceOneAppPerSuite with KeystoreMock with AuthBuilder {

  object Selectors extends BaseSelectors

  implicit val system = ActorSystem("test")

  def testVerifiedEmail = Email("verified", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)

  def testUnVerifiedEmail = Email("unverified", "GG", linkSent = true, verified = false, returnLinkEmailSent = true)

  implicit def mat: Materializer = Materializer(system)
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val mockCreateGGWAccount = app.injector.instanceOf[CreateGGWAccount]
  lazy val mockCreateNewGGWAccount = app.injector.instanceOf[CreateNewGGWAccount]
  lazy val mockCreateNewAccount = app.injector.instanceOf[createNewAccount]
  lazy val mockVerifyYourEmail = app.injector.instanceOf[verifyYourEmail]


  class Setup {
    val controller = new EmailVerificationController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockEmailService,
      mockCompanyRegistrationConnector,
      mockMcc,
      mockVerifyYourEmail,
      mockCreateGGWAccount,
      mockCreateNewGGWAccount,
      mockCreateNewAccount
    )(
      appConfig,
      global
    ) {
      override lazy val createGGWAccountUrl = "testURL"
      override lazy val callbackUrl = "testCallBack"
      override lazy val frontEndUrl = "/testFrontEndUrl"
    }
  }

  "verifyShow" should {
    "redirect to sign-in when not logged in" in new Setup {
      showWithUnauthorisedUser(controller.verifyShow) {
        result =>
          status(result) mustBe 303
          redirectLocation(result) map {
            _ must include("/bas-gateway/sign-in")
          }
      }
    }


    "display Confirm your email address page" in new Setup {
      val email = "We are going to send a message to verified. Check your junk folder. If it’s not there, you’ll need to start again or we can resend it If we resend an email, any previous links will expire."
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))

      when(mockEmailService.fetchEmailBlock(ArgumentMatchers.eq("regid"))(ArgumentMatchers.any[HeaderCarrier]())).
        thenReturn(Some(testVerifiedEmail))

      showWithAuthorisedUser(controller.verifyShow)(
        result => {
          status(result) mustBe 200
          val document = Jsoup.parse(contentAsString(result))
          document.title must include("Confirm your email address")
          document.select(Selectors.p(1)).text mustBe email
        }
      )
    }
  }

  "resendVerificationLink" should {
    val authResult = new ~(
      new ~(
        new ~(
          Name(None, None),
          Some("fakeEmail")
        ), Some(Credentials("provId", "provType"))
      ), Some("extID")
    )
    "redirect to email verification page when resend is link is clicked" in new Setup {
      val email = "unverified"
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))

      when(mockEmailService.fetchEmailBlock(ArgumentMatchers.eq("regid"))(ArgumentMatchers.any[HeaderCarrier]())).
        thenReturn(Some(testUnVerifiedEmail))

      when(mockEmailService.sendVerificationLink(ArgumentMatchers.eq(email), ArgumentMatchers.eq("regid"), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any())).
        thenReturn(Some(false))

      showWithAuthorisedUserRetrieval(controller.resendVerificationLink, authResult) {
        result =>
          status(result) mustBe 303
          redirectLocation(result) map {
            _ must include("/sent-an-email")
          }
      }
    }
  }

  "createShow" should {
    "go to create-account page" in new Setup {
      val result = await(controller.createShow(FakeRequest()))
      status(result) mustBe 200
      val document = Jsoup.parse(contentAsString(result))
      document.title must include("You need to create a new Government Gateway account")
      document.select(Selectors.p(1)).text mustBe "The Government Gateway account you signed in with doesn’t have an email address linked to it." +
        " You need to create a new Government Gateway account including your email address to use this service."
    }
  }

  "createGGWAccountAffinityShow" should {
    "go to incorrect-account-type page" in new Setup {
      val result = await(controller.createGGWAccountAffinityShow(FakeRequest()))
      val document = Jsoup.parse(contentAsString(result))
      document.title must include("You’ve signed in with the wrong type of account")
      document.selectFirst("h1").text mustBe "You’ve signed in with the wrong type of account"
      document.select(Selectors.p(1)).text must include("This service only works with Government Gateway accounts that have been set up for organisations.")
    }
  }

  "createNewGGWAccountShow" should {
    "go to incorrect-service page" in new Setup {
      val result = await(controller.createNewGGWAccountShow(FakeRequest()))
      bodyOf(result) contains "Create a new Government Gateway account"
      val document = Jsoup.parse(contentAsString(result))
      document.title must include("You need to create a new Government Gateway account")
      document.selectFirst("h1").text mustBe "You need to create a new Government Gateway account"
      document.select(Selectors.p(1)).text must include("already been used")
    }
  }

  "createSubmit" should {
    "redirect the user to the welcome page from create new account" in new Setup {
      val result = controller.createSubmit(FakeRequest())
      status(result) mustBe 303
      redirectLocation(result) mustBe Some("/register-your-company/setting-up-new-limited-company")
    }
  }

  "createGGWAccountSubmit" should {
    "redirect the user to the sign-out page from create org account" in new Setup {
      val result = controller.createGGWAccountSubmit(FakeRequest())
      redirectLocation(result) mustBe Some("/register-your-company/sign-out")
    }
  }

}
