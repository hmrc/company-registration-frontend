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

import javax.inject.Inject

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{DecryptionError, PayloadError, SessionRegistration}
import views.html.error_template_restart

import scala.util.{Failure, Success}

class CorporationTaxSummaryControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                                    val keystoreConnector: KeystoreConnector,
                                                    val handOffService: HandOffService,
                                                    val appConfig: FrontendAppConfig,
                                                    val compRegConnector: CompanyRegistrationConnector,
                                                    val handBackService: HandBackService,
                                                    val messagesApi: MessagesApi) extends CorporationTaxSummaryController

trait CorporationTaxSummaryController extends FrontendController with AuthFunction with I18nSupport with SessionRegistration {
  val handBackService : HandBackService

  implicit val appConfig: FrontendAppConfig

  //HO4
  def corporationTaxSummary(requestData : String) : Action[AnyContent] = Action.async {
    implicit _request =>
      ctAuthorisedHandoff("HO4", requestData) {
        registeredHandOff("HO4", requestData) { _ =>
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
}