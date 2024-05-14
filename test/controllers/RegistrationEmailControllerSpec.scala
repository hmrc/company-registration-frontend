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
import controllers.reg.RegistrationEmailController
import helpers.SCRSSpec
import models.{Email, RegistrationEmailModel}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, _}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.reg.{RegistrationEmail => RegistrationEmailView}

import scala.concurrent.Future


class RegistrationEmailControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with MockitoSugar with AuthBuilder {
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockRegistrationEmailView = app.injector.instanceOf[RegistrationEmailView]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val name: Name = Name(None, None)

  class Setup {
    object TestController extends RegistrationEmailController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockEmailService,
      mockCompanyRegistrationConnector,
      mockMcc,
      mockRegistrationEmailView
    )(
      appConfig,
      ec
    ){
      def showLogicFun(email: String = "fakeEmail") = showLogic(email)(HeaderCarrier(), FakeRequest())

      def submitLogicFun(
                          regID: String = "regid",
                          email: String = "fakeEmail",
                          authProvId: String = "provId",
                          extId: String = "extID",
                          r: Request[AnyContent]) = submitLogic(email, regID, authProvId, extId)(HeaderCarrier(), r)
    }


    val mockOfFunction = () => Future.successful(Results.Ok(""))
  }

  "show" should {

    val authResult = new ~(
      name,
      Some("fakeEmail")
    )

    "return 200, with data in keystore" in new Setup {
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      val awaitedFun = await(TestController.showLogicFun())
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))
      showWithAuthorisedUserRetrieval(TestController.show, authResult)(
        result => {
          status(result) mustBe 200
          val document: Document = Jsoup.parse(contentAsString(result))
          document.title must include("Which email address do you want to use for this application?")
          document.getElementById("registrationEmail").attr("value") mustBe "currentEmail"
        }
      )
    }
    "return 200, with no data in keystore" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", None)
      val awaitedFun = await(TestController.showLogicFun())
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))
      showWithAuthorisedUserRetrieval(TestController.show, authResult)(
        result => {
          status(result) mustBe 200
          val document: Document = Jsoup.parse(contentAsString(result))
          document.getElementById("registrationEmail").attr("value") mustBe "currentEmail"
        }
      )
    }
    "return an exception when keystore returns an exception" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGetFailed[RegistrationEmailModel]("RegEmail", new Exception(""))
      val awaitedFun = intercept[Exception](await(TestController.showLogicFun()))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.failed(awaitedFun))
      intercept[Exception](showWithAuthorisedUserRetrieval(TestController.show, authResult)(
        result => {
          await(result)
        }
      ))
    }

  }

  "submit" should {
    val validEmail = Email("foo@bar.com", "SCP", false, true, false)

    "return 400 when invalid data used " in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )
      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "a@b.com")

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))


      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe BAD_REQUEST
          val document = Jsoup.parse(contentAsString(result))
          document.getElementById("registrationEmail").attr("value") mustBe "currentEmail"
      }

    }

    "return 303 and redirect to CompletionCapacity route when success on currentEmail and sendLink returns true (meaning email verified) and SCP verified is false" in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      mockAuthorisedUser(Future.successful(Some(false)))

      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "currentEmail")

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(true)))

      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
      }
    }

    "return 303 and redirect to CompletionCapacity route when success on currentEmail and sendLink returns true (meaning email verified) and SCP verified is true" in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      mockAuthorisedUser(Future.successful(Some(true)))
      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "currentEmail")
      when(mockEmailService.saveEmailBlock(ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(validEmail)))
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(true)))

      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
      }
    }

    "return 303 and redirect to Email Verification show route when success on currentEmail and sendLink returns false meaning email NOT verified and email not verified in SCP " in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      mockAuthorisedUser(Future.successful(Some(false)))
      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "currentEmail")

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(false)))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.verification.routes.EmailVerificationController.verifyShow.url
      }
    }

    "return 303 and redirect to Email Verification show route when success on currentEmail and sendLink returns false meaning email NOT verified and email is verified in SCP " in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      mockAuthorisedUser(Future.successful(Some(true)))
      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "currentEmail")
      when(mockEmailService.saveEmailBlock(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(validEmail)))
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(false)))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
      }
    }


    "return 303 and redirect to Completion Capacity show route when success on currentEmail and sendLink returns None meaning email NOT verified  but email is verified in SCP " in new Setup {

      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      mockAuthorisedUser(Future.successful(Some(true)))
      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "currentEmail")
      when(mockEmailService.saveEmailBlock(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(Some(validEmail)))
      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      when(mockEmailService.sendVerificationLink(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(None))

      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.reg.routes.CompletionCapacityController.show.url
      }
    }

    "return 303 and redirect to RegistrationEmailConfirmation route when success on differentEmail" in new Setup {
      val authResult = new ~(
        new ~(
          new ~(
            name,
            Some("fakeEmail")
          ), Some(Credentials("provId", "provType"))
        ), Some("extID")
      )

      val cm = CacheMap("", Map())

      val req = FakeRequest().withFormUrlEncodedBody("registrationEmail" -> "differentEmail", "DifferentEmail" -> "my@email.com")

      mockKeystoreFetchAndGet[String]("registrationID", Some("regid"))
      mockKeystoreFetchAndGet[RegistrationEmailModel]("RegEmail", Some(RegistrationEmailModel("currentEmail", Some("differentEmail"))))
      mockKeystoreCache[RegistrationEmailModel]("RegEmail", RegistrationEmailModel("currentEmail", Some("differentEmail")), cm)
      val awaitedFun = await(TestController.submitLogicFun("regid", r = req))
      when(mockEmailService.emailVerifiedStatusInSCRS(ArgumentMatchers.any(),
        ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(Future.successful(awaitedFun))

      submitWithAuthorisedUserRetrieval(TestController.submit, req, authResult) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get mustBe controllers.reg.routes.RegistrationEmailConfirmationController.show.url

      }
    }
  }
}