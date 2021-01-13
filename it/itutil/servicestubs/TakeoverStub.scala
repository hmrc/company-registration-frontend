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

package itutil.servicestubs

import itutil.WiremockHelper
import models.TakeoverDetails
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.Json

trait TakeoverStub extends WiremockHelper {
  this: GuiceOneServerPerSuite =>
  def takeoverUrl(registrationId: String) = s"/company-registration/corporation-tax-registration/$registrationId/takeover-details"

  def stubGetTakeoverDetails(registrationId: String, status: Int, optTakeoverDetails: Option[TakeoverDetails] = None): Unit = {
    val jsonBody = optTakeoverDetails match {
      case Some(takeoverDetails) => Json.toJson(takeoverDetails).toString
      case None => "{}"
    }

    stubGet(takeoverUrl(registrationId), status, jsonBody)
  }

  def stubPutTakeoverDetails(registrationId: String, status: Int, takeoverDetails: TakeoverDetails): Unit = {
    val jsonBody = Json.toJson(takeoverDetails).toString

    stubPut(takeoverUrl(registrationId), jsonBody)(status, jsonBody)
  }

}
