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

package controllers.dashboard

import config.FrontendAuthConnector
import connectors._
import controllers.auth.SCRSRegime
import forms.CancelForm
import play.api.data.Form
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.twirl.api.Html
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.dashboard.{CancelPaye, CancelVat}

import scala.concurrent.Future

object CancelRegistrationController extends CancelRegistrationController{
  val payeConnector = PAYEConnector
  val vatConnector = VATConnector
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait CancelRegistrationController extends FrontendController with Actions with SessionRegistration with MessagesSupport {

  val payeConnector: ServiceConnector
  val vatConnector: ServiceConnector
  val keystoreConnector : KeystoreConnector
  val companyRegistrationConnector : CompanyRegistrationConnector

  def showCancelPAYE: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        showCancelService(payeConnector,views.html.dashboard.CancelPaye(CancelForm.form.fill(false)))
  }

  val submitCancelPAYE: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
            submitCancelService(payeConnector,
              (a:Form[Boolean]) => views.html.dashboard.CancelPaye(a))
            }

  def showCancelVAT: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
    implicit request =>
        showCancelService(vatConnector,views.html.dashboard.CancelVat(CancelForm.form.fill(false)))
  }

  val submitCancelVAT: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async{
    implicit user =>
      implicit request =>
        submitCancelService(vatConnector,
          (a:Form[Boolean]) => views.html.dashboard.CancelVat(a))
  }

  private[controllers] def showCancelService(service:ServiceConnector, cancelPage:Html) (implicit request: Request[AnyContent]):Future[Result] = {
    checkStatuses { regID =>
      service.canStatusBeCancelled(regID)(service.getStatus)(hc).map(_ =>
        Ok(cancelPage))
    } recoverWith {
      case a: cantCancelT => Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
    }
  }

  private[controllers] def submitCancelService(service:ServiceConnector,cancelPage: (Form[Boolean]) => Html)(implicit request: Request[AnyContent]):Future[Result] = {
    checkStatuses { regID =>
      CancelForm.form.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest(cancelPage(errors))),
        success =>
          if(success) {
            service.cancelReg(regID)(service.getStatus)(hc).map { _ =>
              Redirect(routes.DashboardController.show())
            }
          }
          else{
            Future.successful(Redirect(routes.DashboardController.show()))
          })
    }
  }
}
