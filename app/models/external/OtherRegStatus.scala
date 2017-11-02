/*
 * Copyright 2017 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class OtherRegStatus(status: String, lastUpdate: Option[DateTime], ackRef: Option[String], cancelURL:Option[String], restartURL:Option[String])

object OtherRegStatus {

  implicit val reads = (
    (__ \ "status").read[String] and
    (__ \ "lastUpdate").readNullable[DateTime](dateTimeReads) and
    (__ \ "ackRef").readNullable[String] and
    (__ \ "cancelURL").readNullable[String] and
    (__ \ "restartURL").readNullable[String]
  )(OtherRegStatus.apply _)

  def dateTimeReads: Reads[DateTime] = new Reads[DateTime] {
    override def reads(json: JsValue): JsResult[DateTime] = {
      val dt = json.as[ZonedDateTime]
      JsSuccess(DateTime.parse(dt.toString))
    }
  }
}

object Statuses {
  final val NOT_ENABLED = "notEnabled"
  final val NOT_STARTED = "notStarted"
  final val NOT_ELIGIBLE = "notEligible"
  final val UNAVAILABLE = "unavailable"
}
