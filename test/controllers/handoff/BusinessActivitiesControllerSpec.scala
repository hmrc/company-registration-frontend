/*
 * Copyright 2018 HM Revenue & Customs
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
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.HandOffServiceImpl
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{DecryptionError, PayloadError}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class BusinessActivitiesControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with WithFakeApplication with AuthBuilder {

  class Setup {
    val controller = new BusinessActivitiesController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handOffService = mockHandOffService
      val handBackService = mockHandBackService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val appConfig = mockAppConfig
    }
  }

  val externalID = Some("extID")

  "BusinessActivitiesController" should {
    "use the correct auth connector" in {
      BusinessActivitiesController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct keystore connector" in {
      BusinessActivitiesController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand off service" in {
      BusinessActivitiesController.handOffService shouldBe HandOffServiceImpl
    }
  }

  "BusinessActivitiesHandOff" should {
    "return a 303 if accessing without authorisation" in new Setup {
      showWithUnauthorisedUser(controller.businessActivities) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
    "return a 303 when keystore returns none but has authorisation" in new Setup {
        mockKeystoreFetchAndGet("registrationID", None)

        when(mockHandOffService.buildBusinessActivitiesPayload(Matchers.any(), Matchers.any())(Matchers.any()))
          .thenReturn(Future.successful(Some("testUrl" -> validEncryptedBusinessActivities)))

        showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
          result =>
            status(result) shouldBe SEE_OTHER
        }
      }

    "return a 303 if accessing with authorisation" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandOffService.buildBusinessActivitiesPayload(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(Some("testUrl" -> validEncryptedBusinessActivities)))

      showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }

    "return a bad request if a url and payload are not returned" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandOffService.buildBusinessActivitiesPayload(Matchers.any(), Matchers.any())(Matchers.any()))
        .thenReturn(Future.successful(None))

      showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "businessActivitiesBack" should {

    "return a 303 if submitting without authorisation" in new Setup {
      showWithUnauthorisedUser(controller.businessActivitiesBack(validEncryptedBusinessActivities)) {
        result =>
          status(result) shouldBe SEE_OTHER
          val url = authUrl("HO3b", validEncryptedBusinessActivities)
          redirectLocation(result) shouldBe Some(url)
      }
    }

    "return a 400 if sending an empty request with authorisation" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(""))(Matchers.any[HeaderCarrier]))
        .thenReturn(Failure(DecryptionError))

      showWithAuthorisedUser(controller.businessActivitiesBack("")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a 400 if a payload error is returned from hand back service" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(validEncryptedBusinessActivities))(Matchers.any[HeaderCarrier]))
        .thenReturn(Failure(PayloadError))

      showWithAuthorisedUser(controller.businessActivitiesBack(validEncryptedBusinessActivities)) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a 303 if submitting with request data and with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(validEncryptedBusinessActivities))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(Json.toJson(validBusinessActivitiesPayload))))

      showWithAuthorisedUser(controller.businessActivitiesBack(validEncryptedBusinessActivities)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some("/register-your-company/loan-payments-dividends")
      }
    }
    "return a 303 if submitting with request data with authorisation but keystore has expired" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(validEncryptedBusinessActivities))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(Json.toJson(validBusinessActivitiesPayload))))

      showWithAuthorisedUser(controller.businessActivitiesBack(validEncryptedBusinessActivities)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(s"/register-your-company/post-sign-in?handOffID=HO3b&payload=$validEncryptedBusinessActivities")
      }
     }


  }
}
