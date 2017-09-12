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

package controllers.reg

import config.FrontendAuthConnector
import connectors._
import controllers.auth.SCRSRegime
import forms.CancelPayeForm
import models.CancelPayeModel
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.reg.CancelPaye
import uk.gov.hmrc.play.frontend.auth.Actions
import utils.{MessagesSupport, SessionRegistration}

import scala.concurrent.Future

object CancelPayeController extends CancelPayeController{
  val payeConnector = PAYEConnector
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait CancelPayeController extends FrontendController with Actions with SessionRegistration with MessagesSupport {

  val payeConnector: PAYEConnector
  val keystoreConnector : KeystoreConnector
  val companyRegistrationConnector : CompanyRegistrationConnector

  def show = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        checkStatuses { regID =>
          payeConnector.canStatusBeCancelled(regID)(payeConnector.getStatus)(hc).map {a =>
          Ok(views.html.reg.CancelPaye(CancelPayeForm.form.fill(CancelPayeModel(false))))
          }
        }recover{
          case a:cantCancelT => Redirect(routes.SignInOutController.postSignIn(None))
        }
  }

  val submit = AuthorisedFor(taxRegime = SCRSRegime(), pageVisibility = GGConfidence).async {
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
}
