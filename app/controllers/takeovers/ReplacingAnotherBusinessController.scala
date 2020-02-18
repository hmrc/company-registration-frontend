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
import controllers.reg.ControllerErrorHandler
import controllers.reg.routes.AccountingDatesController
import controllers.takeovers.routes.OtherBusinessNameController
import forms.takeovers.ReplacingAnotherBusinessForm
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.TakeoverService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.ReplacingAnotherBusiness

import scala.concurrent.Future

@Singleton
class ReplacingAnotherBusinessController @Inject()(val authConnector: PlayAuthConnector,
                                                   val takeoverService: TakeoverService,
                                                   val compRegConnector: CompanyRegistrationConnector,
                                                   val keystoreConnector: KeystoreConnector,
                                                   val scrsFeatureSwitches: SCRSFeatureSwitches,
                                                   val messagesApi: MessagesApi
                                                  )(implicit val appConfig: FrontendAppConfig
                                                  ) extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        if (scrsFeatureSwitches.takeovers.enabled) {
          for {
            optTakeoverInformation <- takeoverService.getTakeoverDetails(regId)
          } yield {
            val form: Form[Boolean] = optTakeoverInformation match {
              case Some(takeoverDetails) =>
                ReplacingAnotherBusinessForm.form.fill(takeoverDetails.replacingAnotherBusiness)
              case None =>
                ReplacingAnotherBusinessForm.form
            }
            Ok(ReplacingAnotherBusiness(form))
          }
        } else {
          Future.failed(new NotFoundException("Takeovers feature switch was not enabled."))
        }
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { regId =>
        ReplacingAnotherBusinessForm.form.bindFromRequest.fold(
          errors =>
            Future.successful(BadRequest(ReplacingAnotherBusiness(errors))),
          replacingAnotherBusiness =>
            takeoverService.updateReplacingAnotherBusiness(regId, replacingAnotherBusiness) map {
              _ =>
                if (replacingAnotherBusiness) {
                  Redirect(OtherBusinessNameController.show())
                } else {
                  Redirect(AccountingDatesController.show())
                }
            }
        )
      }
    }
  }
}