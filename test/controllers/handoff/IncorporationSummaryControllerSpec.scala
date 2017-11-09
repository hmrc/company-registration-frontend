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
import models.SummaryHandOff
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

class IncorporationSummaryControllerSpec extends SCRSSpec with PayloadFixture with WithFakeApplication {

  class Setup {
    object TestController extends IncorporationSummaryController {
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handOffService = mockHandOffService
      val handBackService = mockHandBackService
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
    }
  }

  "IncorporationSummaryController" should {
    "use the correct auth connector" in {
      IncorporationSummaryController.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct key store connector" in {
      IncorporationSummaryController.keystoreConnector shouldBe KeystoreConnector
    }

    "use the correct hand off service" in {
      IncorporationSummaryController.handOffService shouldBe HandOffService
    }

    "use the correct hand back service" in {
      IncorporationSummaryController.handBackService shouldBe HandBackService
    }
  }

  "HMRC CT Summary hand off " should {
    "send the correct Json" in new Setup {
      Json.toJson(summaryHandOffModelPayload).toString() shouldBe summaryHandOffJson
    }

    "return a 303 if user details are retrieved" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandOffService.summaryHandOff(Matchers.any[HeaderCarrier](), Matchers.any[AuthContext]()))
        .thenReturn(Future.successful(Some(("testLink",summaryEncryptedPayload))))

      when(mockHandOffService.buildHandOffUrl(Matchers.eq("testLink"), Matchers.eq(summaryEncryptedPayload)))
        .thenReturn(s"testLink?request=$summaryEncryptedPayload")

      AuthBuilder.showWithAuthorisedUser(TestController.incorporationSummary, mockAuthConnector) {
        result =>
          implicit val user: AuthContext = AuthBuilder.createTestUser
          status(result) shouldBe SEE_OTHER
          val redirect = redirectLocation(result).get
          Jwe.decrypt[SummaryHandOff](redirect.split("request=").apply(1)).get.journey_id shouldBe "testJourneyID"
      }
    }

    "return a 400 if no link or payload are returned" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandOffService.summaryHandOff(Matchers.any[HeaderCarrier](), Matchers.any[AuthContext]()))
        .thenReturn(Future.successful(None))

      AuthBuilder.showWithAuthorisedUser(TestController.incorporationSummary, mockAuthConnector) {
        result =>
          implicit val user: AuthContext = AuthBuilder.createTestUser
          status(result) shouldBe BAD_REQUEST
      }
    }
  }

  "returnToCorporationTaxSummary" should {
    val payload = Json.obj(
      "user_id" -> Json.toJson("testUserID"),
      "journey_id" -> Json.toJson("testJourneyID"),
      "hmrc" -> Json.obj(),
      "ch" -> Json.obj(),
      "links" -> Json.obj()
    )

    "return a 303 when hand back service decrypts the reverse hand off payload successfully" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToCorporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }

    "return a 400 when hand back service errors while decrypting the payload" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToCorporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }

    "return a 400 when hand back service decrypts the payload successfully but the Json is malformed" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(PayloadError)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToCorporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe BAD_REQUEST
      }
    }
    "return a 303 when request is made with auth but keystore does not exist" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val encryptedPayload = Jwe.encrypt[JsValue](payload).get

      when(mockHandBackService.processCompanyNameReverseHandBack(Matchers.eq(encryptedPayload))(Matchers.any[AuthContext], Matchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(payload)))

      AuthBuilder.showWithAuthorisedUser(TestController.returnToCorporationTaxSummary(encryptedPayload), mockAuthConnector) {
        result =>
          status(result) shouldBe SEE_OTHER
      }
    }
  }
}
