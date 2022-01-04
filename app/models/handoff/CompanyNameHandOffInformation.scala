/*
 * Copyright 2022 HM Revenue & Customs
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

package models.handoff

import java.util.TimeZone

import org.apache.commons.lang3.time.FastDateFormat
import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

object DateWriter {
  implicit def dateTimeWrites = new Writes[DateTime] {
    private val dateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZ", TimeZone.getTimeZone("UTC"))

    def writes(dt: DateTime): JsValue = JsString(dateFormat.format(dt.getMillis))
  }
}



case class CompanyNameHandOffInformation(handoffType : String, handoffTime : DateTime, data : JsObject)

case class CompanyNameHandOffInformationReturned(handoffType : String, data : JsObject)

object CompanyNameHandOffInformation {
  implicit val dateWriter = DateWriter.dateTimeWrites
  implicit val format = Json.format[CompanyNameHandOffInformation]
}

object CompanyNameHandOffInformationReturned {
  implicit val format = Json.format[CompanyNameHandOffInformationReturned]
}
