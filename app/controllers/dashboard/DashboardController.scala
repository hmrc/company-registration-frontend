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

package controllers.dashboard

import config.{AppConfig, FrontendAppConfig, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import controllers.reg.ControllerErrorHandler
import play.api.Logger
import play.api.mvc.{Action, AnyContent}
import services._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SCRSExceptions, SessionRegistration}

import scala.concurrent.Future

object DashboardController extends DashboardController with ServicesConfig {
  val authConnector                = FrontendAuthConnector
  val keystoreConnector            = KeystoreConnector
  val dashboardService             = DashboardService
  val companyRegistrationConnector = CompanyRegistrationConnector
  val companiesHouseURL            = getConfString("coho-service.sign-in", throw new Exception("Could not find config for coho-sign-in url"))
  override val appConfig =  FrontendAppConfig
}

trait DashboardController extends FrontendController with AuthFunction with CommonService with SCRSExceptions
with ControllerErrorHandler with SessionRegistration with MessagesSupport {
  implicit val appConfig: AppConfig

  val companiesHouseURL: String
  val dashboardService: DashboardService

  val show: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedPostSignIn { authDetails =>
        registered { regId =>
          dashboardService.checkForEmailMismatch(regId, authDetails) flatMap { _ =>
            dashboardService.buildDashboard(regId, authDetails.enrolments) map {
              case DashboardBuilt(dash) => Ok(views.html.dashboard.Dashboard(dash, companiesHouseURL))
              case CouldNotBuild => Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails())
              case RejectedIncorp => Ok(views.html.reg.RegistrationUnsuccessful())
            } recover {
              case ex => Logger.error(s"[Dashboard Controller] [Show] buildDashboard returned an error ${ex.getMessage}", ex)
                InternalServerError(defaultErrorPage)
            }
          }
        }
      }
  }


  def submit: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None, None, None)))
  }

  def rejected = Action {
    implicit request =>
      Ok(views.html.reg.RegistrationUnsuccessful())
  }
}
