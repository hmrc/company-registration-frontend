/*
 * Copyright 2023 HM Revenue & Customs
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

package models.external

import java.time.ZonedDateTime

import java.time._
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class OtherRegStatus(status: String, lastUpdate: Option[LocalDateTime], ackRef: Option[String], cancelURL:Option[String], restartURL:Option[String])

object OtherRegStatus {

  implicit val format = Json.format[OtherRegStatus]


  implicit val reads = (
    (__ \ "status").read[String] and
    (__ \ "lastUpdate").readNullable[LocalDateTime](dateTimeReads) and
    (__ \ "ackRef").readNullable[String] and
    (__ \ "cancelURL").readNullable[String] and
    (__ \ "restartURL").readNullable[String]
  )(OtherRegStatus.apply _)

  def dateTimeReads: Reads[LocalDateTime] = new Reads[LocalDateTime] {
    override def reads(json: JsValue): JsResult[LocalDateTime] = {
      val dt = json.as[ZonedDateTime]
      JsSuccess(LocalDateTime.parse(dt.toString))
    }
  }
}

object Statuses {
  final val NOT_ENABLED = "notEnabled"
  final val NOT_STARTED = "notStarted"
  final val NOT_ELIGIBLE = "notEligible"
  final val UNAVAILABLE = "unavailable"
}
