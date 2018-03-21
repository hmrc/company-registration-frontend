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

package controllers.reg

import config.{AppConfig, FrontendAppConfig, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import play.api.mvc.Action
import services.DeleteSubmissionService
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.reg.RegistrationUnsuccessful

import scala.concurrent.Future


object RegistrationUnsuccessfulController extends RegistrationUnsuccessfulController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val deleteSubService = DeleteSubmissionService
  val companyRegistrationConnector = CompanyRegistrationConnector
  override val appConfig =  FrontendAppConfig
}

trait RegistrationUnsuccessfulController extends FrontendController with AuthFunction with SessionRegistration with MessagesSupport {

  implicit val appConfig: AppConfig

  val deleteSubService: DeleteSubmissionService

  def show = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(RegistrationUnsuccessful()))
    }
  }

  def submit = Action.async { implicit request =>
    ctAuthorised {
      registered { regId =>
        deleteSubService.deleteSubmission(regId) flatMap {
          case true => keystoreConnector.remove() map {
            _ => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
          }
          case false => Future.successful(InternalServerError)
        }
      }
    }
  }
}
