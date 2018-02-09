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

import config.FrontendAuthConnector
import connectors.KeystoreConnector
import controllers.auth.AuthFunction
import play.api.mvc.{Action, AnyContent}
import services.CommonService
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SCRSExceptions}
import views.html.reg.LimitReached

import scala.concurrent.Future

object LimitReachedController extends LimitReachedController with ServicesConfig {
  val authConnector = FrontendAuthConnector
  //$COVERAGE-OFF$
  val cohoUrl = getConfString("coho-service.web-incs", throw new Exception("Couldn't find Coho url"))
  //$COVERAGE-ON$
  val keystoreConnector = KeystoreConnector
}

trait LimitReachedController extends FrontendController with AuthFunction with CommonService with SCRSExceptions with MessagesSupport {

  val cohoUrl: String

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      Future.successful(Ok(LimitReached(cohoUrl)))
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Redirect(cohoUrl))
  }
}
