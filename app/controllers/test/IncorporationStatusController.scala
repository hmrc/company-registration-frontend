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

package controllers.test

import config.FrontendAuthConnector
import connectors.DynamicStubConnector
import uk.gov.hmrc.play.frontend.controller.FrontendController
import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.auth.Actions
import utils.MessagesSupport

import scala.concurrent.Future

object IncorporationStatusController extends IncorporationStatusController {
  val authConnector = FrontendAuthConnector
  val chApiConnector = DynamicStubConnector
}


trait IncorporationStatusController extends FrontendController with Actions with MessagesSupport {

  val chApiConnector : DynamicStubConnector

  def getIncorporationStatus(id : String) = Action.async { implicit request =>
    chApiConnector.getIncorporationStatus(id).flatMap {
      list => Future.successful(Ok(views.html.endpoints.IncorporationStatusEndpoint(list)))
    }
  }
}
