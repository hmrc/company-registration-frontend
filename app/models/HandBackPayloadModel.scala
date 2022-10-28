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

package models

import models.connectors.ConfirmationReferences
import play.api.libs.json.{JsObject, Json}

// TODO - check for usage and either remove or move to handoff package

case class HandBackPayloadModel(OID: String,
                                return_url: String,
                                fullName : String,
                                email : String,
                                companyName : String,
                                registeredOfficeAddress : CHROAddress)

object HandBackPayloadModel {
  implicit val formats = Json.format[HandBackPayloadModel]
}

case class RegistrationConfirmationPayload(user_id : String,
                                           journey_id : String,
                                           transaction_id : String,
                                           payment_reference : Option[String],
                                           payment_amount : Option[String],
                                           ch : JsObject,
                                           hmrc : JsObject,
                                           language: String,
                                           links : JsObject)



object RegistrationConfirmationPayload {
  implicit val formats = Json.format[RegistrationConfirmationPayload]

  def getReferences(references : RegistrationConfirmationPayload) : ConfirmationReferences =
    ConfirmationReferences(
      transactionId = references.transaction_id,
      paymentReference = references.payment_reference,
      paymentAmount = references.payment_amount,
      ""
    )

}
