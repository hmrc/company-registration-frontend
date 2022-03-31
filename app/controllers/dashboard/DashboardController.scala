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

package controllers.dashboard

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import javax.inject.Inject
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.{SCRSExceptions, SessionRegistration}
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{RegistrationUnsuccessful => RegistrationUnsuccessfulView}
import scala.concurrent.{ExecutionContext, Future}

class DashboardController @Inject()(val authConnector: PlayAuthConnector,
                                        val keystoreConnector: KeystoreConnector,
                                        val compRegConnector: CompanyRegistrationConnector,
                                        val dashboardService: DashboardService,
                                        val controllerComponents: MessagesControllerComponents,
                                        val controllerErrorHandler: ControllerErrorHandler,
                                        viewDashboard: DashboardView,
                                        viewRegistrationUnsuccessful: RegistrationUnsuccessfulView)
                                       (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext) extends AuthenticatedController with CommonService with SCRSExceptions
   with SessionRegistration with I18nSupport with Logging {
  lazy val companiesHouseURL = appConfig.servicesConfig.getConfString("coho-service.sign-in", throw new Exception("Could not find config for coho-sign-in url"))


  val show: Action[AnyContent] = Action.async {
    implicit request =>

      ctAuthorisedPostSignIn { authDetails =>
        registered { regId =>
          dashboardService.checkForEmailMismatch(regId, authDetails) flatMap { _ =>
            dashboardService.buildDashboard(regId, authDetails.enrolments) map {
              case DashboardBuilt(dash) => Ok(viewDashboard(dash, companiesHouseURL))
              case CouldNotBuild => Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails)
              case RejectedIncorp => Ok(viewRegistrationUnsuccessful())
            } recover {
              case ex => logger.error(s"[Dashboard Controller] [Show] buildDashboard returned an error ${ex.getMessage}", ex)
                InternalServerError(controllerErrorHandler.defaultErrorPage)
            }
          }
        }
      }
  }


  def submit: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn(None, None, None)))
  }
}
