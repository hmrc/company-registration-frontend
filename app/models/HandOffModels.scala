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

package models

import play.api.libs.json.{JsObject, Json}

// TODO - check for usage and either remove or move to handoff package

case class AccountingDatesHandOffModel(OID: String,
                         return_url: String,
                         address: Option[Address])

case class SummaryHandOff(user_id : String,
                          journey_id : String,
                          hmrc : JsObject,
                          ch : Option[JsObject],
                          links : JsObject)

object SummaryHandOff {
  implicit val format = Json.format[SummaryHandOff]
}

object AccountingDatesHandOffModel {
  implicit val format = Json.format[AccountingDatesHandOffModel]
}
