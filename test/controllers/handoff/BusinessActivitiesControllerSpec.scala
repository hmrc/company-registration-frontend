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
import config.{AppConfig, LangConstants}
import controllers.reg.ControllerErrorHandler
import fixtures.{LoginFixture, PayloadFixture}
import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.Lang
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.language.LanguageUtils
import utils.{DecryptionError, JweCommon, PayloadError}
import views.html.{error_template, error_template_restart}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class BusinessActivitiesControllerSpec extends SCRSSpec with PayloadFixture with LoginFixture with GuiceOneAppPerSuite with AuthBuilder {

  lazy val errorTemplatePage = app.injector.instanceOf[error_template]
  lazy val errorTemplateRestartPage = app.injector.instanceOf[error_template_restart]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]
  override lazy val mockAppConfig = app.injector.instanceOf[AppConfig]

  class Setup {
    val controller = new BusinessActivitiesController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockHandOffService,
      mockCompanyRegistrationConnector,
      mockHandBackService,
      mockMcc,
      mockControllerErrorHandler,
      app.injector.instanceOf[HandOffUtils],
      errorTemplatePage,
      errorTemplateRestartPage,
      mockLanguageService
    )(
      mockAppConfig,
      global
    )
  val jweInstance = () => app.injector.instanceOf[JweCommon]
  }
  val externalID = Some("extID")

  "BusinessActivitiesHandOff" should {
    "return a 303 if accessing without authorisation" in new Setup {
      showWithUnauthorisedUser(controller.businessActivities) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }
    "return a 303 when keystore returns none but has authorisation" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)

      when(mockHandOffService.buildBusinessActivitiesPayload(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("testUrl" -> validEncryptedBusinessActivities(jweInstance()))))

      showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }

    "return a 303 if accessing with authorisation" in new Setup {

      val payload = validEncryptedBusinessActivities(jweInstance())
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandOffService.buildBusinessActivitiesPayload(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some("testUrl" -> payload)))

      when(mockHandOffService.buildHandOffUrl(ArgumentMatchers.eq("testUrl"), ArgumentMatchers.eq(payload)))
        .thenReturn(s"testUrl?request=$payload")



      showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
        result =>
          status(result) mustBe SEE_OTHER
      }
    }

    "return a bad request if a url and payload are not returned" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))

      when(mockHandOffService.buildBusinessActivitiesPayload(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any())(ArgumentMatchers.any()))
        .thenReturn(Future.successful(None))

      showWithAuthorisedUserRetrieval(controller.businessActivities, externalID) {
        result =>
          status(result) mustBe BAD_REQUEST
      }
    }
  }

  "businessActivitiesBack" should {

    "return a 303 if submitting without authorisation" in new Setup {
      val payload = validEncryptedBusinessActivities(jweInstance())
      showWithUnauthorisedUser(controller.businessActivitiesBack(payload)) {
        result =>
          status(result) mustBe SEE_OTHER
          val url = authUrl("HO3b", payload)
          redirectLocation(result) mustBe Some(url)
      }
    }

    "return a 400 if sending an empty request with authorisation" in new Setup {

      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(""))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Failure(DecryptionError))

      showWithAuthorisedUser(controller.businessActivitiesBack("")) {
        result =>
          status(result) mustBe BAD_REQUEST
          redirectLocation(result) mustBe None
      }
    }

    "return a 400 if a payload error is returned from hand back service" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val payload = validEncryptedBusinessActivities(jweInstance())
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(payload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Failure(PayloadError))

      showWithAuthorisedUser(controller.businessActivitiesBack(payload)) {
        result =>
          status(result) mustBe BAD_REQUEST
          redirectLocation(result) mustBe None
      }
    }

    "return a 303 if submitting with request data and with authorisation (updating the stored language in CompReg BE)" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      val payload = validEncryptedBusinessActivities(jweInstance())
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(payload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(Json.toJson(validBusinessActivitiesPayload))))
      when(mockLanguageService.updateLanguage(ArgumentMatchers.eq("12345"), ArgumentMatchers.eq(Lang(LangConstants.english)))(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      showWithAuthorisedUser(controller.businessActivitiesBack(payload)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe
            Some("/register-your-company/loan-payments-dividends")
      }
    }
    "return a 303 if submitting with request data with authorisation but keystore has expired" in new Setup {
      mockKeystoreFetchAndGet("registrationID", None)
      val payload = validEncryptedBusinessActivities(jweInstance())
      when(mockHandBackService.processBusinessActivitiesHandBack(eqTo(payload))(ArgumentMatchers.any[HeaderCarrier]))
        .thenReturn(Future.successful(Success(Json.toJson(validBusinessActivitiesPayload))))

      showWithAuthorisedUser(controller.businessActivitiesBack(payload)) {
        result =>
          status(result) mustBe SEE_OTHER
          redirectLocation(result) mustBe Some(s"/register-your-company/post-sign-in?handOffID=HO3b&payload=${payload}")
      }
    }
  }
}