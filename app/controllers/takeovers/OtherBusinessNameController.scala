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

package controllers.takeovers

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import controllers.reg.routes.AccountingDatesController
import controllers.takeovers.routes.ReplacingAnotherBusinessController
import forms.takeovers.OtherBusinessNameForm
import javax.inject.{Inject, Singleton}
import models.TakeoverDetails
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.TakeoverService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.NotFoundException
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.takeovers.{OtherBusinessName => OtherBusinessNameView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OtherBusinessNameController @Inject()(val authConnector: PlayAuthConnector,
                                            val takeoverService: TakeoverService,
                                            val compRegConnector: CompanyRegistrationConnector,
                                            val keystoreConnector: KeystoreConnector,
                                            val scrsFeatureSwitches: SCRSFeatureSwitches,
                                            val controllerComponents: MessagesControllerComponents,
                                            val controllerErrorHandler: ControllerErrorHandler,
                                            view: OtherBusinessNameView
                                           )(implicit val appConfig: FrontendAppConfig,
                                             val ec: ExecutionContext
                                           ) extends AuthenticatedController with SessionRegistration with I18nSupport {

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        if (scrsFeatureSwitches.takeovers.enabled) {
          for {
            optTakeoverInformation <- takeoverService.getTakeoverDetails(regId)
          } yield {
            optTakeoverInformation match {
              case Some(TakeoverDetails(false, _, _, _, _)) =>
                Redirect(AccountingDatesController.show())
              case None =>
                Redirect(ReplacingAnotherBusinessController.show())
              case Some(TakeoverDetails(_, Some(businessName), _, _, _)) =>
                Ok(view(OtherBusinessNameForm.form.fill(businessName)))
              case _ =>
                Ok(view(OtherBusinessNameForm.form))
            }
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
        OtherBusinessNameForm.form.bindFromRequest.fold(
          errors =>
            Future.successful(BadRequest(view(errors))),
          otherBusinessName => {
            takeoverService.updateBusinessName(regId, otherBusinessName) map {
              _ => Redirect(routes.OtherBusinessAddressController.show())
            }
          }
        )
      }
    }
  }
}
