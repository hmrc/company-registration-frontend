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

package controllers.reg

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import forms.errors.DeskproForm
import javax.inject.{Inject, Singleton}
import models.ConfirmationReferencesSuccessResponse
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{CommonService, DeskproService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import utils.{SCRSExceptions, SessionRegistration}
import views.html.reg.{Confirmation => ConfirmationView}
import views.html.errors.{submissionFailed => submissionFailedView}
import views.html.errors.{deskproSubmitted => deskproSubmittedView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmationController @Inject()(val authConnector: PlayAuthConnector,
                                       val compRegConnector: CompanyRegistrationConnector,
                                       val keystoreConnector: KeystoreConnector,
                                       val deskproService: DeskproService,
                                       val controllerComponents: MessagesControllerComponents,
                                       val controllerErrorHandler: ControllerErrorHandler,
                                       confirmationView: ConfirmationView,
                                       submissionFailedView: submissionFailedView,
                                       deskproSubmittedView: deskproSubmittedView
                                      )(implicit val appConfig: FrontendAppConfig, implicit val ec: ExecutionContext) extends AuthenticatedController with SessionRegistration with CommonService
  with SCRSExceptions with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      for {
        regID <- fetchRegistrationID
        references <- compRegConnector.fetchConfirmationReferences(regID)
      } yield references match {
        case ConfirmationReferencesSuccessResponse(ref) => Ok(confirmationView(ref))
        case _ =>
          Logger.error(s"[ConfirmationController] [show] could not find acknowledgement ID for reg ID: $regID")
          InternalServerError(controllerErrorHandler.defaultErrorPage)
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
      Future.successful(Ok(submissionFailedView(DeskproForm.form)))
    }
  }

  val submitTicket: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedOptStr(Retrievals.userDetailsUri) { uri =>
      fetchRegistrationID.flatMap(regID =>
        DeskproForm.form.bindFromRequest.fold(
          errors => Future.successful(BadRequest(submissionFailedView(errors))),
          success => deskproService.submitTicket(regID, success, uri) map {
            _ => Redirect(controllers.reg.routes.ConfirmationController.submittedTicket())
          }
        )
      )
    }
  }

  val submittedTicket: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(deskproSubmittedView()))
    }
  }
}