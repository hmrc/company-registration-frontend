/*
 * Copyright 2018 HM Revenue & Customs
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

import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, HandOffService, HandOffServiceImpl, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.{error_template, error_template_restart}

import scala.util.{Failure, Success}

object BusinessActivitiesController extends BusinessActivitiesController {
  val authConnector = FrontendAuthConnector
  val handOffService = HandOffServiceImpl
  val keystoreConnector = KeystoreConnector
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait BusinessActivitiesController extends FrontendController with AuthFunction with SessionRegistration with ControllerErrorHandler with MessagesSupport {

  val handOffService : HandOffService
  val handBackService : HandBackService

  //HO3
  val businessActivities: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { regID =>
          handOffService.buildBusinessActivitiesPayload(regID, externalID).map {
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
            case Success(_) => Redirect(controllers.reg.routes.TradingDetailsController.show())
            case Failure(PayloadError) => BadRequest(error_template_restart("3b", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("3b", "DecryptionError"))
            case _ => InternalServerError(defaultErrorPage)
          }
        }
      }
  }
}
