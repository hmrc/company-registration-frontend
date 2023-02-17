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
import controllers.auth.AuthenticatedController
import controllers.reg.{ControllerErrorHandler, routes => regRoutes}
import forms.takeovers.WhoAgreedTakeoverForm
import models.TakeoverDetails
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TakeoverService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.WhoAgreedTakeover

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhoAgreedTakeoverController @Inject()(val authConnector: PlayAuthConnector,
                                            val takeoverService: TakeoverService,
                                            val compRegConnector: CompanyRegistrationConnector,
                                            val keystoreConnector: KeystoreConnector,
                                            val scrsFeatureSwitches: SCRSFeatureSwitches,
                                            val controllerComponents: MessagesControllerComponents,
                                            val controllerErrorHandler: ControllerErrorHandler,
                                            view: WhoAgreedTakeover
                                           )(implicit val appConfig: AppConfig, val ec: ExecutionContext
                                           ) extends AuthenticatedController with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        takeoverService.getTakeoverDetails(regId).flatMap {
          case Some(TakeoverDetails(false, _, _, _, _)) =>
            Future.successful(Redirect(regRoutes.AccountingDatesController.show))
          case Some(TakeoverDetails(_, None, _, _, _)) =>
            Future.successful(Redirect(routes.OtherBusinessNameController.show))
          case Some(TakeoverDetails(_, _, None, _, _)) =>
            Future.successful(Redirect(routes.OtherBusinessAddressController.show))
          case None =>
            Future.successful(Redirect(routes.ReplacingAnotherBusinessController.show))
          case Some(TakeoverDetails(_, Some(businessName), _, Some(prepopName), _)) =>
            Future.successful(Ok(view(WhoAgreedTakeoverForm.form.fill(prepopName), businessName)))
          case Some(TakeoverDetails(_, Some(businessName), _, None, _)) =>
            Future.successful(Ok(view(WhoAgreedTakeoverForm.form, businessName)))
        }
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { regId =>
        WhoAgreedTakeoverForm.form.bindFromRequest.fold(
          formWithErrors =>
            takeoverService.getTakeoverDetails(regId).flatMap {
              case Some(TakeoverDetails(_, Some(businessName), _, _, _)) =>
                Future.successful(BadRequest(view(formWithErrors, businessName)))
              case _ =>
                Future.successful(Redirect(routes.WhoAgreedTakeoverController.show))
            },
          previousOwnersName => {
            takeoverService.updatePreviousOwnersName(regId, previousOwnersName).map {
              _ => Redirect(routes.PreviousOwnersAddressController.show)
            }
          }
        )
      }
    }
  }
}
