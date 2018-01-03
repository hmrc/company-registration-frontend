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
import controllers.auth.SCRSHandOffRegime
import play.api.Logger
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, NavModelNotFoundException}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.error_template_restart

import scala.util.{Failure, Success}

object CorporationTaxSummaryController extends CorporationTaxSummaryController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector

}

trait CorporationTaxSummaryController extends FrontendController with Actions with MessagesSupport with SessionRegistration {

  val handBackService : HandBackService

  //HO4
  def corporationTaxSummary(requestData : String) : Action[AnyContent] = AuthorisedFor(taxRegime = SCRSHandOffRegime("HO4", requestData), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { a =>
          handBackService.processSummaryPage1HandBack(requestData).map {
            case Success(_) => Redirect(controllers.reg.routes.SummaryController.show())
            case Failure(PayloadError) => BadRequest(error_template_restart("4", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("4", "DecryptionError"))
            case unknown => {
              Logger.warn(s"[CorporationTaxSummaryController][corporationTaxSummary] HO4 Unexpected result, sending to post-sign-in : ${unknown}")
              Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
          } recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
  }
}
