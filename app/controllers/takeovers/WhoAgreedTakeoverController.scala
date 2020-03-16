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

package controllers.takeovers

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.{ControllerErrorHandler, routes => regRoutes}
import forms.takeovers.WhoAgreedTakeoverForm
import javax.inject.{Inject, Singleton}
import models.TakeoverDetails
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.TakeoverService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.WhoAgreedTakeover

import scala.concurrent.Future

@Singleton
class WhoAgreedTakeoverController @Inject()(val authConnector: PlayAuthConnector,
                                            val takeoverService: TakeoverService,
                                            val compRegConnector: CompanyRegistrationConnector,
                                            val keystoreConnector: KeystoreConnector,
                                            val scrsFeatureSwitches: SCRSFeatureSwitches
                                           )(implicit val appConfig: FrontendAppConfig,
                                             val messagesApi: MessagesApi
                                           ) extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        if (scrsFeatureSwitches.takeovers.enabled) {
          takeoverService.getTakeoverDetails(regId).flatMap {
            case Some(TakeoverDetails(false, _, _, _, _)) =>
              Future.successful(Redirect(regRoutes.AccountingDatesController.show()))
            case Some(TakeoverDetails(_, None, _, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessNameController.show()))
            case Some(TakeoverDetails(_, _, None, _, _)) =>
              Future.successful(Redirect(routes.OtherBusinessAddressController.show()))
            case None =>
              Future.successful(Redirect(routes.ReplacingAnotherBusinessController.show()))
            case Some(TakeoverDetails(_, Some(businessName), _, Some(prepopName), _)) =>
              Future.successful(Ok(WhoAgreedTakeover(WhoAgreedTakeoverForm.form.fill(prepopName), businessName)))
            case Some(TakeoverDetails(_, Some(businessName), _, None, _)) =>
              Future.successful(Ok(WhoAgreedTakeover(WhoAgreedTakeoverForm.form, businessName)))
          }
        }
        else {
          Future.failed(new NotFoundException("Takeovers feature switch was not enabled."))
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
                Future.successful(BadRequest(WhoAgreedTakeover(formWithErrors, businessName)))
              case _ =>
                Future.successful(Redirect(routes.WhoAgreedTakeoverController.show()))
            },
          previousOwnersName => {
            takeoverService.updatePreviousOwnersName(regId, previousOwnersName).map {
              _ => Redirect(routes.PreviousOwnersAddressController.show())
            }
          }
        )
      }
    }
  }
}
