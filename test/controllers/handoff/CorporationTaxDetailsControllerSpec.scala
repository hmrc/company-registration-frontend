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

package controllers.handoff

import builders.AuthBuilder
import config.{AppConfig, LangConstants}
import controllers.reg.ControllerErrorHandler
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import models.CHROAddress
import models.handoff.CompanyNameHandOffIncoming
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.Lang
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import utils.{DecryptionError, JweCommon, PayloadError}
import views.html.error_template_restart

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class CorporationTaxDetailsControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with GuiceOneAppPerSuite with AuthBuilder {

  lazy val errorTemplateRestartPage = app.injector.instanceOf[error_template_restart]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  class Setup {

    val testController = new CorporationTaxDetailsController (
      mockAuthConnector,
      mockKeystoreConnector,
      mockHandOffService,
      mockCompanyRegistrationConnector,
      mockHandBackService,
      mockMcc,
      errorTemplateRestartPage,
      mockLanguageService
    )(
      mockAppConfig,ec
    )

    val jweInstance = () => app.injector.instanceOf[JweCommon]
  }

  "Sending a GET to the HandBackController companyNameHandBack" should {

    val handBackPayload = CompanyNameHandOffIncoming(
      Some("RegID"),
      "FAKE_OPEN_CONNECT",
      "TestCompanyName",
      CHROAddress("", "", Some(""), "", "", Some(""), Some(""), Some("")),
      "testJuri",
      "txid",
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"ch" : 1}""").as[JsObject],
      LangConstants.english,
      Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject])

    "return a SEE_OTHER if submitting without authorisation" in new Setup {
      val payload = firstHandBackEncrypted(jweInstance())
      showWithUnauthorisedUser(testController.corporationTaxDetails(payload.get)) {
        result =>
          status(result) mustBe SEE_OTHER
          val url = authUrl("HO2", payload.get)
          redirectLocation(result) mustBe Some(url)
      }
    }

    "return a BAD_REQUEST if sending an empty request with authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(ArgumentMatchers.eq(""))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Failure(DecryptionError))

      showWithAuthorisedUser(testController.corporationTaxDetails("")) {
        result =>
          status(result) mustBe BAD_REQUEST
          redirectLocation(result) mustBe None
      }
    }

    "return a BAD_REQUEST if a payload error is returned from hand back service" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(ArgumentMatchers.eq(payloadEncrypted.get))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Failure(PayloadError))

      showWithAuthorisedUser(testController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) mustBe BAD_REQUEST
          redirectLocation(result) mustBe None
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation (updating the stored language in CompReg BE)" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("1"))
      when(mockHandBackService.processCompanyDetailsHandBack(ArgumentMatchers.eq(payloadEncrypted.get))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(Success(handBackPayload)))
      when(mockLanguageService.updateLanguage(ArgumentMatchers.eq("1"), ArgumentMatchers.eq(Lang(LangConstants.english)))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUser(testController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe
            Some("/register-your-company/principal-place-of-business")
      }
    }

    "return a SEE_OTHER if submitting with request data and with authorisation but keystore has expired" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      when(mockHandBackService.processCompanyDetailsHandBack(ArgumentMatchers.eq(payloadEncrypted.get))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(Success(handBackPayload)))

      showWithAuthorisedUser(testController.corporationTaxDetails(payloadEncrypted.get)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(s"/register-your-company/post-sign-in?handOffID=HO2&payload=${payloadEncrypted.get}")
      }
    }
  }
}
