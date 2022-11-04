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

package controllers.handoff

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler

import javax.inject.Inject
import play.api.i18n.{I18nSupport, Lang}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import utils.{DecryptionError, PayloadError, SessionRegistration}
import views.html.{error_template, error_template_restart}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class BusinessActivitiesController @Inject()(val authConnector: PlayAuthConnector,
                                             val keystoreConnector: KeystoreConnector,
                                             val handOffService: HandOffService,
                                             val compRegConnector: CompanyRegistrationConnector,
                                             val handBackService: HandBackService,
                                             val controllerComponents: MessagesControllerComponents,
                                             val controllerErrorHandler: ControllerErrorHandler,
                                             handOffUtils: HandOffUtils,
                                             error_template: error_template,
                                             error_template_restart: error_template_restart
                                            )
                                            (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with SessionRegistration with I18nSupport {


  //HO3
  val businessActivities: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { regID =>
          handOffService.buildBusinessActivitiesPayload(regID, externalID, handOffUtils.getCurrentLang(request)).map {
            case Some((url, payload)) => Redirect(handOffService.buildHandOffUrl(url, payload))
            case None => BadRequest(error_template("", "", ""))
          }
        } recover {
          case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
      }
  }

  //HO3b
  def businessActivitiesBack(request: String): Action[AnyContent] = Action.async {
    implicit _request =>
      ctAuthorisedHandoff("HO3b", request) {
        registeredHandOff("HO3b", request) { _ =>
          handBackService.processBusinessActivitiesHandBack(request).map {
            case Success(payload) => Redirect(controllers.reg.routes.TradingDetailsController.show).withLang(Lang((payload \ "language").as[String]))
            case Failure(PayloadError) => BadRequest(error_template_restart("3b", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("3b", "DecryptionError"))
            case _ => InternalServerError(controllerErrorHandler.defaultErrorPage)
          }
        }
      }
  }
}
