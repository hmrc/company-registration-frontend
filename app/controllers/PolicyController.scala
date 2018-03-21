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

package controllers

import config.{AppConfig, FrontendAppConfig}
import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.MessagesSupport
import views.html.policies

import scala.concurrent.Future

object PolicyController extends PolicyController {
  override val appConfig =  FrontendAppConfig
}

trait PolicyController extends FrontendController with MessagesSupport {

  implicit val appConfig: AppConfig

  def policyLinks = Action.async { implicit request =>
    Future.successful(Ok(policies()))
  }

}

