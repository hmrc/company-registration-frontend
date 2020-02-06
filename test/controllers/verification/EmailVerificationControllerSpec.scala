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

package controllers.verification

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import builders.AuthBuilder
import config.FrontendAppConfig
import helpers.SCRSSpec
import mocks.{CompanyRegistrationConnectorMock, KeystoreMock, SCRSMocks}
import models.Email
import org.jsoup.Jsoup
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}



class EmailVerificationControllerSpec extends CompanyRegistrationConnectorMock with UnitSpec with MockitoSugar with SCRSMocks with SCRSSpec
  with WithFakeApplication with KeystoreMock with AuthBuilder {

  implicit val system = ActorSystem("test")
  def testVerifiedEmail = Email("verified", "GG", linkSent = true, verified = true, returnLinkEmailSent = true)
  def testUnVerifiedEmail = Email("unverified", "GG", linkSent = true, verified = false, returnLinkEmailSent = true)

  implicit def mat: Materializer = ActorMaterializer()


  class Setup {
    val controller = new EmailVerificationController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      override val compRegConnector = mockCompanyRegistrationConnector
      override val emailVerificationService = mockEmailService
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]

      val createGGWAccountUrl = "testURL"
      val callbackUrl = "testCallBack"
      val frontEndUrl = "/testFrontEndUrl"
    }
  }

  "verifyShow" should {
    "redirect to sign-in when not logged in" in new Setup {
      showWithUnauthorisedUser(controller.verifyShow) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result) map { _ should include("/gg/sign-in") }
      }
    }


    "display Confirm your email address page" in new Setup {
      val email = "verified"
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))

      when(mockEmailService.fetchEmailBlock(Matchers.eq("regid"))(Matchers.any[HeaderCarrier]())).
        thenReturn(Some(testVerifiedEmail))

      showWithAuthorisedUser(controller.verifyShow)(
        result => {
          status(result) shouldBe 200
          val document = Jsoup.parse(contentAsString(result))
          document.title shouldBe "Confirm your email address"
          document.getElementById("description").text should include(email)
        }
      )
    }
  }

  "resendVerificationLink" should {
    val authResult = new ~(
      new ~(
        new ~(
          Name(None,None),
          Some("fakeEmail")
        ), Credentials("provId", "provType")
      ), Some("extID")
    )
    "redirect to email verification page when resend is link is clicked" in new Setup {
      val email = "unverified"
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))

      when(mockEmailService.fetchEmailBlock(Matchers.eq("regid"))(Matchers.any[HeaderCarrier]())).
        thenReturn(Some(testUnVerifiedEmail))

      when(mockEmailService.sendVerificationLink(Matchers.eq(email),Matchers.eq("regid"),Matchers.any(),Matchers.any())(Matchers.any[HeaderCarrier](),Matchers.any())).
        thenReturn(Some(false))

      showWithAuthorisedUserRetrieval(controller.resendVerificationLink, authResult) {
        result =>
          status(result) shouldBe 303
          redirectLocation(result) map {
            _ should include("/sent-an-email")
          }
      }
    }
  }

  "createShow" should {
    "go to create-account page" in new Setup {
      val result = await(controller.createShow(FakeRequest()))
      status(result) shouldBe 200
      val document = Jsoup.parse(contentAsString(result))
      document.title shouldBe "You need to create a new Government Gateway account"
      document.getElementById("description-one").text should include("doesn't have an email address linked")
   }
  }

  "createGGWAccountAffinityShow" should {
    "go to incorrect-account-type page" in new Setup {
      val result = await(controller.createGGWAccountAffinityShow(FakeRequest()))
      val document = Jsoup.parse(contentAsString(result))
      document.title shouldBe "Create Government Gateway Account"
      document.getElementById("main-heading").text shouldBe "You've signed in with the wrong type of account"
      document.getElementById("para-one").text should include("This service only works with Government Gateway accounts that have been set up for organisations.")
    }
  }

  "createNewGGWAccountShow" should {
    "go to incorrect-service page" in new Setup {
      val result = await(controller.createNewGGWAccountShow(FakeRequest()))
      bodyOf(result) contains "Create a new Government Gateway account"
      val document = Jsoup.parse(contentAsString(result))
      document.title shouldBe "Create Government Gateway Account"
      document.getElementById("main-heading").text shouldBe "You need to create a new Government Gateway account"
      document.getElementById("para-one").text should include("already been used")
    }
  }

  "createSubmit" should {
    "redirect the user to the welcome page from create new account" in new Setup {
      val result = controller.createSubmit(FakeRequest())
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("/register-your-company/setting-up-new-limited-company")
    }
  }

  "createGGWAccountSubmit" should {
    "redirect the user to the sign-out page from create org account" in new Setup {
      val result = controller.createGGWAccountSubmit(FakeRequest())
      redirectLocation(result) shouldBe Some("/register-your-company/sign-out")
    }
  }

}
