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

import config.{AppConfig, FrontendAppConfig, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import play.api.Logger
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, HandOffService, HandOffServiceImpl, NavModelNotFoundException}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.error_template_restart

import scala.util.{Failure, Success}

object CorporationTaxDetailsController extends CorporationTaxDetailsController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val handOffService = HandOffServiceImpl
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector
  override val appConfig =  FrontendAppConfig
}

trait CorporationTaxDetailsController extends FrontendController with AuthFunction with SessionRegistration with MessagesSupport {
  val handOffService : HandOffService

  val handBackService : HandBackService
  implicit val appConfig: AppConfig

  //HO2
  def corporationTaxDetails(requestData : String) : Action[AnyContent] = Action.async {
    implicit _request =>
      ctAuthorisedHandoff("HO2", requestData) {
        registeredHandOff("HO2", requestData) { _ =>
          handBackService.processCompanyDetailsHandBack(requestData).map {
            case Success(_) => Redirect(controllers.reg.routes.PPOBController.show())
            case Failure(PayloadError) => BadRequest(error_template_restart("2", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("2", "DecryptionError"))
            case unknown => {
              Logger.warn(s"[CorporationTaxDetailsController][corporationTaxDetails] HO2 Unexpected result, sending to post-sign-in : ${unknown}")
              Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
          } recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }
}
