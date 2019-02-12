/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject.Inject

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import models.{ConfirmationReferencesSuccessResponse, DESFailureRetriable}
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._
import views.html.{error_template, error_template_restart}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class RegistrationConfirmationControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                                       val keystoreConnector: KeystoreConnector,
                                                       val handOffService: HandOffService,
                                                       val appConfig: FrontendAppConfig,
                                                       val compRegConnector: CompanyRegistrationConnector,
                                                       val handBackService: HandBackService,
                                                       val messagesApi: MessagesApi) extends RegistrationConfirmationController

trait RegistrationConfirmationController extends FrontendController with AuthFunction with I18nSupport with SessionRegistration {
  val handBackService: HandBackService

  val handOffService: HandOffService
  implicit val appConfig: FrontendAppConfig

  //HO5.1 & old HO6
  def registrationConfirmation(requestData: String): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedExternalIDIncomplete { externalID =>
        registered {
          regid =>
            handBackService.decryptConfirmationHandback(requestData) flatMap {
              case Success(s) => handBackService.storeConfirmationHandOff(s, regid).flatMap {
                case _ if handBackService.payloadHasForwardLinkAndNoPaymentRefs(s) => getPaymentHandoffResult(externalID)
                case ConfirmationReferencesSuccessResponse(_) => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show()))
                case DESFailureRetriable => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.show()))
                case _ => Future.successful(Redirect(controllers.reg.routes.ConfirmationController.deskproPage()))
              }
              case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("6", "DecryptionError")))
              case unknown => Future.successful {
                Logger.warn(s"[RegistrationConfirmationController][registrationConfirmation] HO6 Unexpected result, sending to post-sign-in : ${unknown}")
                Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
              }
            } recover {
              case _: NavModelNotFoundException =>
                Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
        }
      }
  }

  def paymentConfirmation(requestData: String): Action[AnyContent] = {
    Logger.info("[RegistrationConfirmationController][paymentConfirmation] New Handoff 6")
    registrationConfirmation(requestData)
  }

  private def getPaymentHandoffResult(externalID: Option[String])(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    handOffService.buildPaymentConfirmationHandoff(externalID).map {
      case Some((url, payloadString)) => Redirect(handOffService.buildHandOffUrl(url, payloadString))
      case None => BadRequest(error_template("", "", ""))
    }
  }
}