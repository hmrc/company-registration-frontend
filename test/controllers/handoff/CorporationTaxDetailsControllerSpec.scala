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
import models.CHROAddress
import models.handoff.CompanyNameHandOffIncoming
import org.mockito.Matchers
import org.mockito.Mockito._
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{HandBackService, HandOffServiceImpl}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.{DecryptionError, PayloadError}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import uk.gov.hmrc.http.HeaderCarrier

class CorporationTaxDetailsControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with WithFakeApplication with AuthBuilder {

  class Setup {
    object TestController extends CorporationTaxDetailsController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handOffService = mockHandOffService
      val handBackService = mockHandBackService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
    }
  }

  "CorporationTaxDetailsController" should {
    "use the correct auth connector" in {
      CorporationTaxDetailsController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct key store connector" in {
      CorporationTaxDetailsController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand off service" in {
      CorporationTaxDetailsController.handOffService shouldBe HandOffServiceImpl
    }

    "use the correct hand back service" in {
      CorporationTaxDetailsController.handBackService shouldBe HandBackService
    }
  }

  "Sending a GET to the HandBackController companyNameHandBack" should {

    val handBackPayload = CompanyNameHandOffIncoming(
      Some("RegID"),
      "FAKE_OPEN_CONNECT",
      "TestCompanyName",
      CHROAddress("","",Some(""),"","",Some(""),Some(""),Some("")),
      "testJuri",
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject])

    "return a SEE_OTHER if submitting without authorisation" in new Setup {
      showWithUnauthorisedUser(TestController.corporationTaxDetails(firstHandBackEncrypted.get)) {
        result =>
          status(result) shouldBe SEE_OTHER
          val url = authUrl("HO2", firstHandBackEncrypted.get)
          redirectLocation(result) shouldBe Some(url)
      }
    }

    "return a BAD_REQUEST if sending an empty request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(Matchers.eq(""))(Matchers.any[HeaderCarrier]))
        .thenReturn(Failure(DecryptionError))

      showWithAuthorisedUser(TestController.corporationTaxDetails("")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a BAD_REQUEST if a payload error is returned from hand back service" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(Matchers.eq(payloadEncrypted.get))(Matchers.any[HeaderCarrier]))
        .thenReturn(Failure(PayloadError))

      showWithAuthorisedUser(TestController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(Matchers.eq(payloadEncrypted.get))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      showWithAuthorisedUser(TestController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some("/register-your-company/principal-place-of-business")
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation but keystore has expired" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.processCompanyDetailsHandBack(Matchers.eq(payloadEncrypted.get))(Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      showWithAuthorisedUser(TestController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(s"/register-your-company/post-sign-in?handOffID=HO2&payload=${payloadEncrypted.get}")
      }
    }
  }
}
