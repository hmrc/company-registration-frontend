/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.handoff

import builders.AuthBuilder
import config.AppConfig
import controllers.reg.ControllerErrorHandler
import fixtures.PayloadFixture
import helpers.SCRSSpec
import models.Email
import models.handoff.CompanyNameHandOffIncoming
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.retrieve.{Name, ~}
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DecryptionError, JweCommon, PayloadError}
import views.html.{error_template, error_template_restart}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

class BasicCompanyDetailsControllerSpec extends SCRSSpec with PayloadFixture with GuiceOneAppPerSuite with AuthBuilder {

  lazy val errorTemplatePage = app.injector.instanceOf[error_template]
  lazy val errorTemplateRestartPage = app.injector.instanceOf[error_template_restart]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  lazy implicit val appConfig = app.injector.instanceOf[AppConfig]

  class Setup {

    val testController = new BasicCompanyDetailsController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockHandOffService,
      mockCompanyRegistrationConnector,
      mockHandBackService,
      mockMcc,
      mockControllerErrorHandler,
      errorTemplatePage,
      errorTemplateRestartPage,
      app.injector.instanceOf[HandOffUtils]
    )(
      appConfig,
      global
    )

    val jweInstance = () => app.injector.instanceOf[JweCommon]
  }

  val authDetails = new ~(
    new ~(
      Some(Name(Some("firstName"), Some("lastName"))),
      Some("email")
    ), Some("test")
  )

  "basicCompanyDetails" should {
    "return a 303 and redirect the user to the incorporation frontend stub" when {
      "meta data is fetched from business registration micro-service" in new Setup {

        mockKeystoreFetchAndGet("registrationID", Some("1"))

        when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(Some(Email("foo", "bar", true, true, true))))

        when(mockHandOffService.companyNamePayload(ArgumentMatchers.eq("1"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(("testUrl/basic-company-details", "testEncryptedPayload"))))

        when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.eq("testUrl/basic-company-details"), ArgumentMatchers.eq("testEncryptedPayload")))
          .thenReturn(s"testUrl/basic-company-details?request=testEncryptedPayload")

        showWithAuthorisedUserRetrieval(testController.basicCompanyDetails, authDetails) {
          result =>
            status(result) mustBe SEE_OTHER

            redirectLocation(result).get mustBe "testUrl/basic-company-details?request=testEncryptedPayload"
        }
      }
    }

    "should pass the correct encrypted payload into the query string" in new Setup {

      val encryptedPayload = jweInstance().encrypt[CompanyNameHandOffIncoming](validCompanyNameHandBack).get

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(Some(Email("foo", "bar", true, true, true))))
      when(mockHandOffService.companyNamePayload(ArgumentMatchers.eq("1"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(("testUrl/basic-company-details", encryptedPayload))))

      when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.eq("testUrl/basic-company-details"), ArgumentMatchers.eq(encryptedPayload)))
        .thenReturn(s"testUrl/basic-company-details?request=$encryptedPayload")


      showWithAuthorisedUserRetrieval(testController.basicCompanyDetails, authDetails) {
        result =>
          status(result) mustBe SEE_OTHER
          val encryptedPayload = redirectLocation(result).get.split("request=")(1)
          jweInstance().decrypt[CompanyNameHandOffIncoming](encryptedPayload) mustBe Success(validCompanyNameHandBack)
      }
    }

    "return a 400 and display an error page when nothing is retrieved from user details" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(Some(Email("foo", "bar", true, true, true))))
      when(mockHandOffService.companyNamePayload(ArgumentMatchers.eq("1"), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      showWithAuthorisedUserRetrieval(testController.basicCompanyDetails, authDetails) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }
    "return a Redirect to post sign in if email block is None" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockCompanyRegistrationConnector.retrieveEmail(ArgumentMatchers.any())(ArgumentMatchers.any())).thenReturn(Future.successful(None))
      showWithAuthorisedUserRetrieval(testController.basicCompanyDetails, authDetails) {
        result =>
          status(result) mustBe 303
          redirectLocation(result).get mustBe controllers.reg.routes.SignInOutController.postSignIn().url
      }
    }
  }

  "returnToAboutYou" should {
    val payload = Json.obj(
      "user_id" -> Json.toJson("testUserID"),
      "journey_id" -> Json.toJson("testJourneyID"),
      "hmrc" -> Json.obj(),
      "ch" -> Json.obj(),
      "links" -> Json.obj()
    )

    "return a 303 when hand back service decrypts the reverse hand off payload successfully" in new Setup {
      val encryptedPayload = jweInstance().encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyNameReverseHandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      showWithAuthorisedUser(testController.returnToAboutYou(encryptedPayload)) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }

    "return a 303 when the user is authorised and the query string contains requestData but keystore has expired" in new Setup {
      val encryptedPayload = jweInstance().encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.processCompanyNameReverseHandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      showWithAuthorisedUser(testController.returnToAboutYou(encryptedPayload)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(s"/register-your-company/post-sign-in?handOffID=HO1b&payload=$encryptedPayload")
      }
    }

    "return a 400 when hand back service errors while decrypting the payload" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val encryptedPayload = jweInstance().encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      showWithAuthorisedUser(testController.returnToAboutYou(encryptedPayload)) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }

    "return a 400 when hand back service decrypts the payload successfully but the Json is malformed" in new Setup {
      val encryptedPayload = jweInstance().encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandBackService.processCompanyNameReverseHandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(PayloadError)))

      showWithAuthorisedUser(testController.returnToAboutYou(encryptedPayload)) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }
  }
}
