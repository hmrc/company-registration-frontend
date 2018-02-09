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
import play.api.Logger
import play.api.mvc.{Action, AnyContent}
import services.{HandBackService, HandOffService, HandOffServiceImpl, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.error_template_restart

import scala.util.{Failure, Success}

object IncorporationSummaryController extends IncorporationSummaryController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val handOffService = HandOffServiceImpl
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait IncorporationSummaryController extends FrontendController with AuthFunction with SessionRegistration with ControllerErrorHandler with MessagesSupport {

  val handOffService : HandOffService
  val handBackService : HandBackService

  //HO5
  def incorporationSummary: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { a =>
          handOffService.summaryHandOff(externalID) map {
            case Some((url, payload)) => Redirect(handOffService.buildHandOffUrl(url, payload))
            case None => BadRequest(defaultErrorPage)
          } recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }

  //HO5b
  def returnToCorporationTaxSummary(request: String): Action[AnyContent] = Action.async {
    implicit _request =>
      ctAuthorisedHandoff("HO5b", request) {
        registeredHandOff("HO5b", request) { _ =>
          handBackService.processCompanyNameReverseHandBack(request).map {
            case Success(_) => Redirect(controllers.reg.routes.SummaryController.show())
            case Failure(PayloadError) => BadRequest(error_template_restart("5b", "PayloadError"))
            case Failure(DecryptionError) => BadRequest(error_template_restart("5b", "DecryptionError"))
            case unknown => {
              Logger.warn(s"[IncorporationSummaryController][returnToCorporationTaxSummary] HO5b Unexpected result, sending to post-sign-in : ${unknown}")
              Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
            }
          } recover {
            case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
        }
      }
  }
}
