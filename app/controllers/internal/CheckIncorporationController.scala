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

package controllers.internal

import connectors.{CohoApiBadRequestResponse, CohoApiErrorResponse, CohoApiNoData, CohoApiSuccessResponse}
import play.api.libs.json.{JsNumber, JsObject}
import play.api.mvc.{Action, AnyContent}
import services.internal.CheckIncorporationService
import uk.gov.hmrc.play.frontend.controller.FrontendController

object CheckIncorporationController extends CheckIncorporationController {
  val checkIncorporationService = CheckIncorporationService
}

trait CheckIncorporationController extends FrontendController {
  val checkIncorporationService: CheckIncorporationService

  def fetchIncorporation(timeStamp: Option[String], itemsPerPage: Int) : Action[AnyContent] = Action.async {
    implicit request =>
      val RequestedRangeNotSatisfiable = new Status(REQUESTED_RANGE_NOT_SATISFIABLE)
      checkIncorporationService.fetchIncorporationStatus(timeStamp, itemsPerPage) map {
        case CohoApiSuccessResponse(json) => Ok(json)
        case CohoApiBadRequestResponse => BadRequest("Coho submission API returned a 400")
        case CohoApiNoData => RequestedRangeNotSatisfiable("Coho submission API returned a 416 - No incorporations available")
        case CohoApiErrorResponse(ex) => BadGateway("Coho submission API returned an unknown error")
      }
  }
}
