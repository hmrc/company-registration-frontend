/*
 * Copyright 2017 HM Revenue & Customs
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

package controllers.reg

import config.FrontendAuthConnector
import controllers.auth.SCRSRegime
import play.api.mvc.Action
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import services._
import uk.gov.hmrc.play.frontend.auth.Actions
import utils.{MessagesSupport, SCRSExceptions, SessionRegistration}

import scala.concurrent.Future

object DashboardController extends DashboardController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val dashboardService = DashboardService
  val companyRegistrationConnector = CompanyRegistrationConnector

}

trait DashboardController extends FrontendController with Actions with CommonService with SCRSExceptions with ControllerErrorHandler with SessionRegistration with MessagesSupport {

  val dashboardService: DashboardService

  val show = AuthorisedFor(taxRegime = SCRSRegime("post-sign-in"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { regId =>
          dashboardService.buildDashboard(regId) map {
            case DashboardBuilt(dash) => Ok(views.html.reg.Dashboard(dash))
            case CouldNotBuild => Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails())
            case RejectedIncorp => Ok(views.html.reg.RegistrationUnsuccessful())
          } recover {
            case _ => InternalServerError(defaultErrorPage)
          }
        }
  }


  def submit = Action.async { implicit request =>
    Future.successful(Redirect(routes.SignInOutController.postSignIn(None, None, None)))
  }

}
