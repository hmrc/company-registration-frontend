/*
 * Copyright 2021 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import javax.inject.Inject
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{HandBackService, HandOffService, NavModelNotFoundException}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import utils.{DecryptionError, PayloadError, SessionRegistration}
import views.html.error_template_restart

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class IncorporationSummaryController @Inject()(val authConnector: PlayAuthConnector,
                                                   val keystoreConnector: KeystoreConnector,
                                                   val handOffService: HandOffService,
                                                   val compRegConnector: CompanyRegistrationConnector,
                                                   val handBackService: HandBackService,
                                                   val controllerComponents: MessagesControllerComponents,
                                                   val controllerErrorHandler: ControllerErrorHandler,
                                                   error_template_restart: error_template_restart)
                                                  (implicit val appConfig: FrontendAppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with SessionRegistration with I18nSupport {

  //HO5
  def incorporationSummary: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.externalId) { externalID =>
        registered { a =>
          handOffService.summaryHandOff(externalID) map {
            case Some((url, payload)) => Redirect(handOffService.buildHandOffUrl(url, payload))
            case None => BadRequest(controllerErrorHandler.defaultErrorPage)
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