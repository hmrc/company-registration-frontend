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
import forms.CancelPayeForm
import models.CancelPayeModel
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.dashboard.CancelPaye

import scala.concurrent.Future

object CancelRegistrationController extends CancelRegistrationController{
  val payeConnector = PAYEConnector
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait CancelRegistrationController extends FrontendController with Actions with SessionRegistration with MessagesSupport {

  val payeConnector: ServiceConnector
  val keystoreConnector : KeystoreConnector
  val companyRegistrationConnector : CompanyRegistrationConnector

  def showCancelPAYE: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        checkStatuses { regID =>
          payeConnector.canStatusBeCancelled(regID)(payeConnector.getStatus)(hc).map {a =>
          Ok(views.html.dashboard.CancelPaye(CancelPayeForm.form.fill(CancelPayeModel(false))))
          }
        }recover{
          case a:cantCancelT => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
  }

  val submitCancelPAYE: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        checkStatuses { regID =>
          CancelPayeForm.form.bindFromRequest.fold(
            errors =>
              Future.successful(BadRequest(CancelPaye(errors))),
            success => {
              if (success.cancelPaye) {
                payeConnector.cancelReg(regID)(payeConnector.getStatus)(hc).map { a =>
                  Redirect(routes.DashboardController.show())
                }
              }
              else {
                Future.successful(Redirect(routes.DashboardController.show()))
              }
            }
          )
        }
  }

  def showCancelVAT: Action[AnyContent] = ???
}
