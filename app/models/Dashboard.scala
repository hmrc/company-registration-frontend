/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json.{Json, _}

case class Dashboard(companyName: String,
                     incDash: IncorpAndCTDashboard,
                     payeDash: ServiceDashboard,
                     vatDash: ServiceDashboard,
                     hasVATCred: Boolean = false,
                     vatFeatureFlag: Boolean = false)

case class IncorpAndCTDashboard(status : String,
                                submissionDate : Option[String],
                                transId : Option[String],
                                paymentRef : Option[String],
                                crn : Option[String],
                                ctSubmissionDate: Option[String],
                                ackRef: Option[String],
                                ackRefStatus: Option[String],
                                ctutr: Option[String])

object IncorpAndCTDashboard {
  val format = Json.format[IncorpAndCTDashboard]

  private val rds = (
    (__ \ "status").read[String] and
    (__ \ "submissionDate").readNullable[String] and
    (__ \ "confirmationReferences" \ "transaction-id").readNullable[String].orElse(Reads.pure(None)) and
    (__ \ "confirmationReferences" \ "payment-reference").readNullable[String].orElse(Reads.pure(None)) and
    (__ \ "crn").readNullable[String] and
    (__ \ "submissionTimestamp").readNullable[String].map(_.map(DateTime.parse(_).toString("d MMMM yyyy"))) and
    (__ \ "confirmationReferences" \ "acknowledgement-reference").readNullable[String].orElse(Reads.pure(None)) and
    (__ \ "acknowledgementReferences" \ "status").readNullable[String].orElse(Reads.pure(None)) and
    (__ \ "acknowledgementReferences" \ "ctUtr").readNullable[String].orElse(Reads.pure(None))
  )(IncorpAndCTDashboard.apply _)

  def reads(date: Option[String]): Reads[IncorpAndCTDashboard] = new Reads[IncorpAndCTDashboard] {
    def reads(json: JsValue): JsResult[IncorpAndCTDashboard] = {
      val fullJson = date.fold(json.as[JsObject])(d => json.as[JsObject] ++ JsObject(Seq("submissionDate" -> JsString(d))))
      fullJson.validate[IncorpAndCTDashboard](rds)
    }
  }
}

case class ServiceLinks(startURL: String, otrsURL: String, restartURL: Option[String], cancelURL:Option[String])

case class ServiceDashboard(status: String, lastUpdate: Option[String], ackRef: Option[String], links: ServiceLinks, thresholds: Option[Map[String, Int]])