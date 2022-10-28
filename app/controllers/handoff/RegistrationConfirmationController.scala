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
import javax.inject.Inject
import models.{ConfirmationReferencesSuccessResponse, DESFailureRetriable}
import utils.Logging
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc._
import services.{HandBackService, HandOffService, NavModelNotFoundException}
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
                                                   handOffUtils: HandOffUtils
                                                  )
                                                  (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with I18nSupport with SessionRegistration with Logging {

  //HO5.1 & old HO6
  def registrationConfirmation(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedExternalIDIncomplete { externalID =>
        registered {
          regid =>
            handBackService.decryptConfirmationHandback(requestData) flatMap {
              case payload@Success(s) => handBackService.storeConfirmationHandOff(s, regid).flatMap {
                case _ if handBackService.payloadHasForwardLinkAndNoPaymentRefs(s) => getPaymentHandoffResult(externalID)
                case ConfirmationReferencesSuccessResponse(_) => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show).withLang(Lang(payload.get.language)))
                case DESFailureRetriable => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show).withLang(Lang(payload.get.language)))
                case _ => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.deskproPage).withLang(Lang(payload.get.language)))
              }
              case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("6", "DecryptionError")))
              case unknown => Future.successful {
                logger.warn(s"[registrationConfirmation][HO6] Unexpected result, sending to post-sign-in : ${unknown}")
                Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
              }
            } recover {
              case _: NavModelNotFoundException =>
                Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
        }
      }
  }

  def paymentConfirmation(requestData: String): Action[AnyContent] =
    registrationConfirmation(requestData)

  private def getPaymentHandoffResult(externalID: Option[String])(implicit hc: HeaderCarrier, request: MessagesRequest[AnyContent]): Future[Result] = {
    handOffService.buildPaymentConfirmationHandoff(externalID, handOffUtils.getCurrentLang(request)).map {
      case Some((url, payloadString)) => Redirect(handOffService.buildHandOffUrl(url, payloadString))
      case None => BadRequest(error_template("", "", ""))
    }
  }
}