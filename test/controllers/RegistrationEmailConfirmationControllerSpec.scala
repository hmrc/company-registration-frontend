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

package controllers


import builders.AuthBuilder
import config.AppConfig
import controllers.reg.RegistrationEmailConfirmationController
import helpers.SCRSSpec
import mocks.SCRSMocks
import models.{ConfirmRegistrationEmailModel, RegistrationEmailModel}
import org.jsoup.Jsoup
import org.mockito.{ArgumentMatcher, ArgumentMatchers}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import views.html.reg.{ConfirmRegistrationEmail => ConfirmRegistrationEmailView}
import scala.concurrent.Future


class RegistrationEmailConfirmationControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AuthBuilder with SCRSMocks {

  class Setup {
    lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    lazy val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
    lazy val mockConfirmRegistrationEmailView = app.injector.instanceOf[ConfirmRegistrationEmailView]
    
    object TestController extends RegistrationEmailConfirmationController(
      mockEmailService,
      mockAuthConnector,
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      controllerComponents,
      mockConfirmRegistrationEmailView
    )(
      appConfig,
      ec
    ){
      def showLogicFun(f: Future[Result] = TestController.showLogic(HeaderCarrier(), FakeRequest())) = f

      def submitLogicFun(
                          regID: String = "regid",
                          authProviderId: String = "aId",
                          extIdFromAuth: String = "ext",
                          r: Request[AnyContent]) = TestController.submitLogic(regID, authProviderId, extIdFromAuth)(HeaderCarrier(), r)

    }
    val mockOfFunction = () => Future.successful(Results.Ok(""))
  }

  case class funcMatcher(func: () => Future[Result]) extends ArgumentMatcher[() => Future[Result]] {
    override def matches(argument: () => Future[Result]): Boolean = argument match {
      case a: (() => Future[Result]) => true
      case _ => false
    }

  }

  "Sending a GET request to the RegistrationEmailConfirmationController" should {
    "return a 200 with an authorised user where user has already been to the page before" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", Some(ConfirmRegistrationEmailModel(true)))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
      val awaitedFun = await(TestController.showLogicFun())

      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      showWithAuthorisedUser(TestController.show) {
        result =>
          status(result) mustBe OK
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("confirmRegistrationEmail").attr("value") mustBe "true"
      }
    }
    "return a 200 with an authorised user where no data exists in keystore for this page" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      showWithAuthorisedUser(TestController.show) {
        mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", None)
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        val awaitedFun = await(TestController.showLogicFun())

        when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
          ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

        result =>
          status(result) mustBe OK
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("confirmRegistrationEmail").attr("checked") mustBe ""
          document.getElementById("confirmRegistrationEmail-no").attr("checked") mustBe ""
      }
    }
    "return a 303 redirect with an authorised user because RegistrationEmailModel in keystore is None" in new Setup {

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      showWithAuthorisedUser(TestController.show) {
        mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", Some(ConfirmRegistrationEmailModel(true)))
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", None)
        val awaitedFun = await(TestController.showLogicFun())

        when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
          ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("/register-your-company/post-sign-in")

      }
    }
    "return a 303 to sign in whilst requesting with an unauthorised user" in new Setup {
      showWithUnauthorisedUser(TestController.show) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some("http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9970%2Fregister-your-company%2Fpost-sign-in&origin=company-registration-frontend")
      }
    }
  }

  "POSTing the RegistrationEmailConfirmationController" should {
    val authResult = new ~(
      new ~(
        new ~(
          Name(None, None),
          Some("fakeEmail")
        ), Some(Credentials("provId", "provType"))
      ), Some("extID")
    )
    "return a 303" when {
      "posting with valid data - YES redirect to completion capacity" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "true")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(),
          ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(true)))
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
          ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))


        submitWithAuthorisedUserRetrieval(TestController.submit, requestForTest, authResult) {
          result =>
            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some("/register-your-company/relationship-to-company")
        }
      }
      "posting with valid data - NO and redirects and first page of email" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "false")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
          ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

        submitWithAuthorisedUserRetrieval(TestController.submit, requestForTest, authResult) {
          result =>
            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some("/register-your-company/registration-email")
        }
      }
      "posting with missing data from previous page" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "true")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", None)
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
          ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

        submitWithAuthorisedUserRetrieval(TestController.submit, requestForTest, authResult) {
          result =>
            status(result) mustBe SEE_OTHER
            redirectLocation(result) mustBe Some("/register-your-company/post-sign-in")
        }
      }
      "return a 400" when {
        "posting with invalid data" in new Setup {
          mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
          val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "sdkjfhksd")
          mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("sfg@sdf.com", Some("sdgf@dfg.com"))))
          val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
          when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
            ArgumentMatchers.argThat(funcMatcher(mockOfFunction)))(ArgumentMatchers.any(),
            ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

          submitWithAuthorisedUserRetrieval(TestController.submit, requestForTest, authResult) {
            result =>
              status(result) mustBe BAD_REQUEST
              val document = Jsoup.parse(contentAsString(result))
              document.getElementById("confirmRegistrationEmail").attr("checked") mustBe ""
              document.getElementById("confirmRegistrationEmail-no").attr("checked") mustBe ""
          }
        }
      }
    }
  }
}