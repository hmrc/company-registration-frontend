/*
 * Copyright 2020 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.KeystoreConnector
import controllers.auth.AuthenticatedController
import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.CommonService
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.SCRSExceptions
import views.html.reg.LimitReached

import scala.concurrent.{ExecutionContext, Future}

class LimitReachedControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                           val keystoreConnector: KeystoreConnector,
                                           val appConfig: FrontendAppConfig,
                                           val controllerComponents: MessagesControllerComponents)(implicit val ec: ExecutionContext) extends LimitReachedController {

  lazy val cohoUrl = appConfig.servicesConfig.getConfString("coho-service.web-incs", throw new Exception("Couldn't find Coho url"))
}

abstract class LimitReachedController extends AuthenticatedController with CommonService with SCRSExceptions with I18nSupport {
  val cohoUrl: String

  implicit val appConfig: FrontendAppConfig

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(LimitReached(cohoUrl)))
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Redirect(cohoUrl))
  }
}