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

package controllers.dashboard

import config.FrontendAppConfig
import connectors._
import controllers.auth.AuthenticatedController
import forms.CancelForm
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.SessionRegistration
import views.html.dashboard.{CancelPaye => CancelPayeView}
import views.html.dashboard.{CancelVat => CancelVatView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CancelRegistrationController @Inject()(val payeConnector: PAYEConnector,
                                             val vatConnector: VATConnector,
                                             val keystoreConnector: KeystoreConnector,
                                             val authConnector: PlayAuthConnector,
                                             val compRegConnector: CompanyRegistrationConnector,
                                             val controllerComponents: MessagesControllerComponents,
                                             cancelPayeView: CancelPayeView,
                                             canelVatView: CancelVatView)
                                            (implicit val appConfig: FrontendAppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with SessionRegistration with I18nSupport {


  def showCancelPAYE: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      showCancelService(payeConnector, cancelPayeView(CancelForm.form.fill(false)))
    }
  }

  val submitCancelPAYE: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      submitCancelService(payeConnector,
        (a: Form[Boolean]) => cancelPayeView(a))
    }
  }

  def showCancelVAT: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      showCancelService(vatConnector, canelVatView(CancelForm.form.fill(false)))
    }
  }

  val submitCancelVAT: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      submitCancelService(vatConnector,
        (a: Form[Boolean]) => canelVatView(a))
    }
  }

  private[controllers] def showCancelService(service: ServiceConnector, cancelPage: Html)(implicit request: Request[AnyContent]): Future[Result] = {
    checkStatuses { regID =>
      service.canStatusBeCancelled(regID)(service.getStatus)(hc).map(_ => Ok(cancelPage))
    } recoverWith {
      case a: cantCancelT => Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
    }
  }

  private[controllers] def submitCancelService(service: ServiceConnector, cancelPage: (Form[Boolean]) => Html)(implicit request: Request[AnyContent]): Future[Result] = {
    checkStatuses { regID =>
      CancelForm.form.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest(cancelPage(errors))),
        success =>
          if (success) {
            service.cancelReg(regID)(service.getStatus)(hc).map { _ =>
              Redirect(routes.DashboardController.show)
            }
          }
          else {
            Future.successful(Redirect(routes.DashboardController.show))
          })
    }
  }
}