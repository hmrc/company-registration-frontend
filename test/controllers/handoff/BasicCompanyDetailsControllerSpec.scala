/*
 * Copyright 2017 HM Revenue & Customs
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
import config.FrontendAuthConnector
import connectors.KeystoreConnector
import fixtures.PayloadFixture
import helpers.SCRSSpec
import models.handoff.CompanyNameHandOffIncoming
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers._
import services.{HandBackService, HandOffService}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{DecryptionError, Jwe, PayloadError}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import uk.gov.hmrc.http.HeaderCarrier

class BasicCompanyDetailsControllerSpec extends SCRSSpec with PayloadFixture with WithFakeApplication {

  class Setup {
    object TestController extends BasicCompanyDetailsController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handOffService = mockHandOffService
      val handBackService = mockHandBackService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector

    }
  }

  "BasicCompanyDetailsController" should {
    "use the correct auth connector" in {
      BasicCompanyDetailsController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct keystore connector" in {
      BasicCompanyDetailsController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand off service" in {
      BasicCompanyDetailsController.handOffService shouldBe HandOffService
    }

    "use the correct hand back service" in {
      BasicCompanyDetailsController.handBackService shouldBe HandBackService
    }
  }

  "basicCompanyDetails" should {
    "return a 303 and redirect the user to the incorporation frontend stub" when {
      "meta data is fetched from business registration micro-service" in new Setup {

        mockKeystoreFetchAndGet("registrationID", Some("1"))

        when(mockHandOffService.companyNamePayload(Matchers.eq("1"))(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
          .thenReturn(Future.successful(Some(("testUrl/basic-company-details", "testEncryptedPayload"))))

        when(mockHandOffService.buildHandOffUrl(Matchers.eq("testUrl/basic-company-details"), Matchers.eq("testEncryptedPayload")))
          .thenReturn(s"testUrl/basic-company-details?request=testEncryptedPayload")

        AuthBuilder.showWithAuthorisedUser(TestController.basicCompanyDetails, mockAuthConnector) {
          result =>
            status(result) shouldBe SEE_OTHER

            redirectLocation(result).get shouldBe "testUrl/basic-company-details?request=testEncryptedPayload"
        }
      }
    }

    "should pass the correct encrypted payload into the query string" in new Setup {

      val encryptedPayload = Jwe.encrypt[CompanyNameHandOffIncoming](validCompanyNameHandBack).get

      mockKeystoreFetchAndGet("registrationID", Some("1"))

      when(mockHandOffService.companyNamePayload(Matchers.eq("1"))(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Some(("testUrl/basic-company-details", encryptedPayload))))

      when(mockHandOffService.buildHandOffUrl(Matchers.eq("testUrl/basic-company-details"), Matchers.eq(encryptedPayload)))
        .thenReturn(s"testUrl/basic-company-details?request=$encryptedPayload")


      AuthBuilder.showWithAuthorisedUser(TestController.basicCompanyDetails, mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          val encryptedPayload = redirectLocation(result).get.split("request=")(1)
          Jwe.decrypt[CompanyNameHandOffIncoming](encryptedPayload) shouldBe Success(validCompanyNameHandBack)
      }
    }

    "return a 400 and display an error page when nothing is retrieved from user details" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("1"))

      when(mockHandOffService.companyNamePayload(Matchers.eq("1"))(Matchers.any[AuthContext](), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(None))

      AuthBuilder.showWithAuthorisedUser(TestController.basicCompanyDetails, mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
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
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToAboutYou(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "return a 303 when keystore returns None" in new Setup {
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToAboutYou(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }

    "return a 400 when hand back service errors while decrypting the payload" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToAboutYou(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a 400 when hand back service decrypts the payload successfully but the Json is malformed" in new Setup {
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(PayloadError)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToAboutYou(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }
}
