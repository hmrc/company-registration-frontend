/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.reg

import javax.inject.Inject

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.errors.DeskproForm
import models.ConfirmationReferencesSuccessResponse
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.{CommonService, DeskproService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSExceptions, SessionRegistration}
import views.html.reg.Confirmation

import scala.concurrent.Future

class ConfirmationControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                           val compRegConnector: CompanyRegistrationConnector,
                                           val keystoreConnector: KeystoreConnector,
                                           val deskproService: DeskproService,
                                           val appConfig: FrontendAppConfig,
                                           val messagesApi: MessagesApi) extends ConfirmationController

trait ConfirmationController extends FrontendController with AuthFunction with SessionRegistration with CommonService
  with SCRSExceptions with ControllerErrorHandler with I18nSupport {

  val compRegConnector: CompanyRegistrationConnector
  val deskproService : DeskproService
  implicit val appConfig: FrontendAppConfig

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      for {
        regID <- fetchRegistrationID
        references <- compRegConnector.fetchConfirmationReferences(regID)
      } yield references match {
        case ConfirmationReferencesSuccessResponse(ref) => Ok(Confirmation(ref))
        case _ =>
          Logger.error(s"[ConfirmationController] [show] could not find acknowledgement ID for reg ID: $regID")
          InternalServerError(defaultErrorPage)
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Redirect(controllers.dashboard.routes.DashboardController.show()))
    }
  }

  val deskproPage: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(views.html.errors.submissionFailed(DeskproForm.form)))
    }
  }

  val submitTicket: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedOptStr(Retrievals.userDetailsUri) { uri =>
      fetchRegistrationID.flatMap(regID =>
        DeskproForm.form.bindFromRequest.fold(
          errors => Future.successful(BadRequest(views.html.errors.submissionFailed(errors))),
          success => deskproService.submitTicket(regID, success, uri) map {
            _ => Redirect(controllers.reg.routes.ConfirmationController.submittedTicket())
          }
        )
      )
    }
  }

  val submittedTicket: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(views.html.errors.deskproSubmitted()))
    }
  }
}