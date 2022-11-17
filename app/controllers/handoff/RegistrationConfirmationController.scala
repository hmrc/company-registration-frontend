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

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import models.handoff.RegistrationConfirmationPayload

import javax.inject.Inject
import models.{ConfirmationReferencesSuccessResponse, DESFailureRetriable}
import utils.Logging
import play.api.i18n.{I18nSupport, Lang}
import play.api.libs.json.JsValue
import play.api.mvc._
import services.{HandBackService, HandOffService, LanguageService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import utils._
import views.html.{error_template, error_template_restart}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class RegistrationConfirmationController @Inject()(val authConnector: PlayAuthConnector,
                                                   val keystoreConnector: KeystoreConnector,
                                                   val handOffService: HandOffService,
                                                   val compRegConnector: CompanyRegistrationConnector,
                                                   val handBackService: HandBackService,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   error_template_restart: error_template_restart,
                                                   error_template: error_template,
                                                   handOffUtils: HandOffUtils,
                                                   languageService: LanguageService)
                                                  (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with I18nSupport with SessionRegistration with Logging {

  //HO5.1 & old HO6
  def registrationConfirmation(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedExternalIDIncomplete { externalID =>
        registered { regId =>
          handBackService.decryptConfirmationHandback(requestData) flatMap {
            case Success(payload) =>
              successResponseHandling(regId, externalID, payload)
            case Failure(DecryptionError) =>
              Future.successful(BadRequest(error_template_restart("6", "DecryptionError")))
            case unknown =>
              logger.warn(s"[registrationConfirmation][HO6] Unexpected result, sending to post-sign-in : ${unknown}")
              Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
          } recover {
            case _: NavModelNotFoundException =>
              Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }

  private[controllers] def successResponseHandling(regId: String, externalID: Option[String], payload: RegistrationConfirmationPayload)
                                                  (implicit hc: HeaderCarrier, request: MessagesRequest[AnyContent]): Future[Result] =
    for {
      confirmationResponse <- handBackService.storeConfirmationHandOff(payload, regId)
      lang = Lang(payload.language)
      _ <- languageService.updateLanguage(regId, lang)
      redirect <- confirmationResponse match {
        case _ if handBackService.payloadHasForwardLinkAndNoPaymentRefs(payload) => getPaymentHandoffResult(externalID, payload.language)
        case ConfirmationReferencesSuccessResponse(_) => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show).withLang(lang))
        case DESFailureRetriable => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show).withLang(lang))
        case _ => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.deskproPage).withLang(lang))
      }
    } yield redirect

  def paymentConfirmation(requestData: String): Action[AnyContent] =
    registrationConfirmation(requestData)

  private def getPaymentHandoffResult(externalID: Option[String], currentLanguage: String)(implicit hc: HeaderCarrier, request: MessagesRequest[AnyContent]): Future[Result] = {
    handOffService.buildPaymentConfirmationHandoff(externalID, currentLanguage).map {
      case Some((url, payloadString)) => Redirect(handOffService.buildHandOffUrl(url, payloadString))
      case None => BadRequest(error_template("", "", ""))
    }
  }
}