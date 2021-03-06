/*
 * Copyright 2021 HM Revenue & Customs
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
import config.FrontendAppConfig
import fixtures.LoginFixture
import helpers.SCRSSpec
import models.handoff.{NavLinks, SummaryPage1HandOffIncoming}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DecryptionError, JweCommon, PayloadError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class CorporationTaxSummaryControllerSpec extends SCRSSpec with LoginFixture with GuiceOneAppPerSuite with AuthBuilder {

  class Setup {

    object TestController extends CorporationTaxSummaryController {
      override val controllerComponents = app.injector.instanceOf[MessagesControllerComponents]
      val authConnector = mockAuthConnector
      val keystoreConnector = mockKeystoreConnector
      val handBackService = mockHandBackService
      val compRegConnector = mockCompanyRegistrationConnector
      implicit val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
      override val messagesApi = app.injector.instanceOf[MessagesApi]
      implicit val ec: ExecutionContext = global
    }

    val jweInstance = () => app.injector.instanceOf[JweCommon]
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
      val encryptedPayload: Option[String] = jweInstance().encrypt[SummaryPage1HandOffIncoming](handBackPayload)

      showWithUnauthorisedUser(TestController.corporationTaxSummary(encryptedPayload.get)) {
        result =>
          status(result) shouldBe SEE_OTHER
          val url = authUrl("HO4", encryptedPayload.get)
          redirectLocation(result) shouldBe Some(url)
      }
    }

    "return a BAD_REQUEST if sending an empty request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processSummaryPage1HandBack(ArgumentMatchers.eq(""))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(DecryptionError)))

      showWithAuthorisedUser(TestController.corporationTaxSummary("")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a BAD_REQUEST if a payload error is returned from hand back service during decryption" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = jweInstance().encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Failure(PayloadError)))

      showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload)) {
        result =>
          status(result) shouldBe BAD_REQUEST
          redirectLocation(result) shouldBe None
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      val encryptedPayload = jweInstance().encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe
            Some("/register-your-company/check-confirm-answers")
      }
    }
    "return a 303 when the user is authorised and the query string contains requestData but keystore has expired" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val encryptedPayload = jweInstance().encrypt[SummaryPage1HandOffIncoming](handBackPayload).get

      when(mockHandBackService.processSummaryPage1HandBack(ArgumentMatchers.eq(encryptedPayload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      showWithAuthorisedUser(TestController.corporationTaxSummary(encryptedPayload)) {
        result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(s"/register-your-company/post-sign-in?handOffID=HO4&payload=$encryptedPayload")
      }
    }
  }
}
