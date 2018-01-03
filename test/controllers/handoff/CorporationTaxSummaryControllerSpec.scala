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
import fixtures.LoginFixture
import helpers.SCRSSpec
import models.handoff.{NavLinks, SummaryPage1HandOffIncoming}
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.HandBackService
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{DecryptionError, Jwe, PayloadError}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import uk.gov.hmrc.http.HeaderCarrier

class CorporationTaxSummaryControllerSpec extends SCRSSpec with LoginFixture with WithFakeApplication {

  class Setup {
    object TestController extends CorporationTaxSummaryController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handBackService = mockHandBackService
      val companyRegistrationConnector = mockCompanyRegistrationConnector
    }
  }

  "CorporationTaxDetailsController" should {
    "use the correct auth connector" in {
      CorporationTaxSummaryController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct key store connector" in {
      CorporationTaxSummaryController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand back service" in {
      CorporationTaxSummaryController.handBackService shouldBe HandBackService
    }
  }

  "Sending a GET to the HandBackController summary1HandBack" should {

    val handBackPayload = SummaryPage1HandOffIncoming(
      "testUserID",
      "testjourneyID",
      Json.obj(),
      Json.obj(),
      NavLinks("testForwardLink", "testReverseLink")
    )

    "return a SEE_OTHER if submitting without authorisation" in new Setup {

      val encryptedPayload = Jwe.encrypt[SummaryPage1HandOffIncoming](handBackPayload)

      val result = TestController.corporationTaxSummary(encryptedPayload.get)(FakeRequest())
      status(result) shouldBe SEE_OTHER
      val url = authUrl("HO4", encryptedPayload.get)
      redirectLocation(result) shouldBe Some(url)
    }

    "return a BAD_REQUEST if sending an empty request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processSummaryPage1HandBack(Matchers.eq(""))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      AuthBuilder.showWithAuthorisedUser(TestController.corporationTaxSummary(""), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a BAD_REQUEST if a payload error is returned from hand back service during decryption" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = Jwe.encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(PayloadError)))

      AuthBuilder.showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = Jwe.encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      AuthBuilder.showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some("/register-your-company/check-confirm-answers")
      }
    }
    "return a SEE_OTHER if submitting with request data and with authorisation but keystore does not exist" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val encryptedPayload = Jwe.encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      AuthBuilder.showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some("/register-your-company/post-sign-in")
      }
    }
  }
}
