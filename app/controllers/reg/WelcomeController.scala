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

import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.reg.Welcome
import utils.{MessagesSupport, SCRSFeatureSwitches}

import scala.concurrent.Future

object WelcomeController extends WelcomeController

trait WelcomeController extends FrontendController with MessagesSupport {

  val show = Action.async { implicit request =>
    Future.successful(Ok(Welcome(SCRSFeatureSwitches.paye.enabled)))
  }
  val submit = Action.async { implicit request =>
    Future.successful(Redirect(routes.ReturningUserController.show()))
  }
}
