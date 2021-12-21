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

package controllers.takeovers

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import controllers.reg.ControllerErrorHandler
import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.SessionRegistration
import views.html.errors.{takeoverInformationNeeded => takeoverInformationNeededView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TakeoverInformationNeededController @Inject()(val authConnector: PlayAuthConnector,
                                                    val keystoreConnector: KeystoreConnector,
                                                    val compRegConnector: CompanyRegistrationConnector,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    val controllerErrorHandler: ControllerErrorHandler,
                                                    view: takeoverInformationNeededView)
                                                   (implicit val appConfig: FrontendAppConfig, val ec: ExecutionContext
                                                   ) extends AuthenticatedController with SessionRegistration {

  def show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus {
        _ => Future.successful(Ok(view()))
      }
    }
  }
}
