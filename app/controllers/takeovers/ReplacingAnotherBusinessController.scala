/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.takeovers

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.reg.{ routes => regRoutes}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import forms.takeovers.ReplacingAnotherBusinessForm
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TakeoverService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.{ReplacingAnotherBusiness => ReplacingAnotherBusinessView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReplacingAnotherBusinessController @Inject()(val authConnector: PlayAuthConnector,
                                                   val takeoverService: TakeoverService,
                                                   val compRegConnector: CompanyRegistrationConnector,
                                                   val keystoreConnector: KeystoreConnector,
                                                   val scrsFeatureSwitches: SCRSFeatureSwitches,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   val controllerErrorHandler: ControllerErrorHandler,
                                                   view: ReplacingAnotherBusinessView
                                                  )(implicit val appConfig: AppConfig, val ec: ExecutionContext
                                                  ) extends AuthenticatedController with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        for {
          optTakeoverInformation <- takeoverService.getTakeoverDetails(regId)
        } yield {
          val form: Form[Boolean] = optTakeoverInformation match {
            case Some(takeoverDetails) =>
              ReplacingAnotherBusinessForm.form.fill(takeoverDetails.replacingAnotherBusiness)
            case None =>
              ReplacingAnotherBusinessForm.form
          }
          Ok(view(form))
        }
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { regId =>
        ReplacingAnotherBusinessForm.form.bindFromRequest.fold(
          errors =>
            Future.successful(BadRequest(view(errors))),
          replacingAnotherBusiness =>
            takeoverService.updateReplacingAnotherBusiness(regId, replacingAnotherBusiness) map {
              _ =>
                if (replacingAnotherBusiness) {
                  Redirect(routes.OtherBusinessNameController.show)
                } else {
                  Redirect(regRoutes.AccountingDatesController.show)
                }
            }
        )
      }
    }
  }
}