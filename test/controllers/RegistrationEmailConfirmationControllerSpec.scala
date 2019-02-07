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

package controllers

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.reg.RegistrationEmailConfirmationController
import helpers.SCRSSpec
import mocks.SCRSMocks
import models.{ConfirmRegistrationEmailModel, RegistrationEmailModel}
import org.jsoup.Jsoup
import org.mockito.Mockito._
import org.mockito.{ArgumentMatcher, Matchers}
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request, Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future


class RegistrationEmailConfirmationControllerSpec extends SCRSSpec with WithFakeApplication with AuthBuilder with SCRSMocks {

  class Setup {

    object TestController extends RegistrationEmailConfirmationController {
      val emailVerificationService = mockEmailService
      val authConnector = mockAuthConnector
      override val keystoreConnector = mockKeystoreConnector
      implicit val appConfig: FrontendAppConfig = fakeApplication.injector.instanceOf[FrontendAppConfig]
      override val compRegConnector = mockCompanyRegistrationConnector
      implicit val hc:HeaderCarrier = HeaderCarrier()
      implicit val fr= FakeRequest()
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      def showLogicFun(f: Future[Result] = TestController.showLogic(HeaderCarrier(),FakeRequest())) = f
      def submitLogicFun(regID: String = "regid", r:Request[AnyContent]) = TestController.submitLogic(regID)(HeaderCarrier(),r)
    }
    val mockOfFunction  =  () => Future.successful(Results.Ok(""))
  }

  case class funcMatcher(func: () => Future[Result]) extends ArgumentMatcher[() => Future[Result]] {
    override def matches(oarg :scala.Any): Boolean = oarg match {
      case a:(() => Future[Result]) => true
      case _ => false
    }
  }

  "Sending a GET request to the RegistrationEmailConfirmationController" should {
    "return a 200 with an authorised user where user has already been to the page before" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", Some(ConfirmRegistrationEmailModel(true)))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
      val awaitedFun = await(TestController.showLogicFun())

      when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))

      showWithAuthorisedUser(TestController.show()) {
        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("confirmRegistrationEmail-true").attr("checked") shouldBe "checked"
      }
    }
    "return a 200 with an authorised user where no data exists in keystore for this page" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      showWithAuthorisedUser(TestController.show()) {
        mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", None)
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        val awaitedFun = await(TestController.showLogicFun())

        when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))

        result =>
          status(result) shouldBe OK
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("confirmRegistrationEmail-true").attr("checked") shouldBe ""
          document.getElementById("confirmRegistrationEmail-false").attr("checked") shouldBe ""
      }
    }
    "return a 303 redirect with an authorised user because RegistrationEmailModel in keystore is None" in new Setup {

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      showWithAuthorisedUser(TestController.show()) {
        mockKeystoreFetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail", Some(ConfirmRegistrationEmailModel(true)))
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", None)
        val awaitedFun = await(TestController.showLogicFun())

        when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")

      }
    }
    "return a 303 to sign in whilst requesting with an unauthorised user" in new Setup {
      showWithUnauthorisedUser(TestController.show()) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("http://localhost:9025/gg/sign-in?accountType=organisation&continue=http%3A%2F%2Flocalhost%3A9970%2Fregister-your-company%2Fpost-sign-in&origin=company-registration-frontend")
      }
    }
  }

  "POSTing the RegistrationEmailConfirmationController" should {
    "return a 303" when {
      "posting with valid data - YES redirect to completion capacity" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "true")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        when(mockEmailService.sendVerificationLink(Matchers.any(), Matchers.any())(Matchers.any(), Matchers.any())).thenReturn(Future.successful(Some(true)))
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))


        submitWithAuthorisedUser(TestController.submit, requestForTest) {
          result =>
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/register-your-company/relationship-to-company")
        }
      }
      "posting with valid data - NO and redirects and first page of email" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "false")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("test@test.com", Some("tester@tester.com"))))
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))

        submitWithAuthorisedUser(TestController.submit, requestForTest) {
          result =>
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/register-your-company/registration-email")
        }
      }
      "posting with missing data from previous page" in new Setup {
        mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
        val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "true")
        mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", None)
        val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
        when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))

        submitWithAuthorisedUser(TestController.submit, requestForTest) {
          result =>
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some("/register-your-company/post-sign-in")
        }
      }
      "return a 400" when {
        "posting with invalid data" in new Setup {
          mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
          val requestForTest = FakeRequest().withFormUrlEncodedBody("confirmRegistrationEmail" -> "sdkjfhksd")
          mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("sfg@sdf.com", Some("sdgf@dfg.com"))))
          val awaitedFun = await(TestController.submitLogicFun(r = requestForTest))
          when(mockEmailService.emailVerifiedStatusInSCRS(Matchers.any(),Matchers.argThat(funcMatcher(mockOfFunction)))(Matchers.any())).thenReturn(Future.successful(awaitedFun))

          submitWithAuthorisedUser(TestController.submit, requestForTest) {
            result =>
              status(result) shouldBe BAD_REQUEST
              val document = Jsoup.parse(contentAsString(result))
              document.getElementById("confirmRegistrationEmail-true").attr("checked") shouldBe ""
              document.getElementById("confirmRegistrationEmail-false").attr("checked") shouldBe ""
          }
        }
      }
    }
  }
}