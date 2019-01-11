/*
 * Copyright 2019 HM Revenue & Customs
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

import config.{AppConfig, FrontendAppConfig, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, HandOffService, HandOffServiceImpl, NavModelNotFoundException}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.{error_template, error_template_restart}

import scala.util.{Failure, Success}

object BasicCompanyDetailsController extends BasicCompanyDetailsController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val handOffService = HandOffServiceImpl
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector
  override val appConfig =  FrontendAppConfig
}

trait BasicCompanyDetailsController extends FrontendController with AuthFunction with SessionRegistration with ControllerErrorHandler with MessagesSupport {

  val handOffService : HandOffService
  val handBackService : HandBackService

  implicit val appConfig: AppConfig

  //HO1
  val basicCompanyDetails: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedBasicCompanyDetails { basicAuth =>
        registered {
          regId =>
            handOffService.companyNamePayload(regId, basicAuth.email, basicAuth.name, basicAuth.externalId) map {
              case Some((url, payload)) => Redirect(handOffService.buildHandOffUrl(url, payload))
              case None => BadRequest(error_template("", "", ""))
            }
        }
      }
  }

  //H01b
  def returnToAboutYou(request: String): Action[AnyContent] = Action.async {
    implicit _request =>
      ctAuthorisedHandoff("HO1b", request) {
        registeredHandOff("HO1b", request) { _ =>
          handBackService.processCompanyNameReverseHandBack(request).map {
            case Success(_) => Redirect(controllers.reg.routes.CompletionCapacityController.show())
            case Failure(PayloadError) => BadRequest(error_template_restart("1b", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("1b", "DecryptionError"))
            case _ => InternalServerError(defaultErrorPage)
          } recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }
}
