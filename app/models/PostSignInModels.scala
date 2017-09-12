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

package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class ThrottleResponse(
     registrationId: String,
     created: Boolean,
     confRefs: Boolean,
     emailData: Option[Email] = None,
     registrationProgress: Option[String] = None
)

object ThrottleResponse {
  implicit val fmtEmail = Format(Email.reads, Email.writes)
  implicit val format = (
     (JsPath \ "registration-id").format[String] and
     (JsPath \ "created").format[Boolean] and
     (JsPath \ "confirmation-reference").format[Boolean] and
     (JsPath \ "email").formatNullable[Email] and
     (JsPath \ "registration-progress").formatNullable[String]
    )(ThrottleResponse.apply, unlift(ThrottleResponse.unapply))
}

case class Email(
  address : String,
  emailType : String,
  linkSent : Boolean,
  verified : Boolean,
  returnLinkEmailSent : Boolean
)

object Email {
  val GG = "GG"
  implicit val reads = (
    (__ \ "address").read[String] and
    (__ \ "type").read[String] and
    (__ \ "link-sent").read[Boolean] and
    (__ \ "verified").read[Boolean] and
    (__ \ "return-link-email-sent").read[Boolean].orElse(Reads.pure(true))
    )(Email.apply _)

  implicit val writes = (
    (__ \ "address").write[String] and
      (__ \ "type").write[String] and
      (__ \ "link-sent").write[Boolean] and
      (__ \ "verified").write[Boolean] and
      (__ \ "return-link-email-sent").write[Boolean]

  )(unlift(Email.unapply))
}

case class EmailVerificationRequest(
  email : String,
  templateId : String,
  templateParameters : Map[String,String],
  linkExpiryDuration : String,
  continueUrl : String
)

object EmailVerificationRequest {
  implicit val format = Json.format[EmailVerificationRequest]
}

case class SendTemplatedEmailRequest(

  to: Seq[String],
  templateId: String,
  parameters: Map[String,String],
  force: Boolean
                                    )
object SendTemplatedEmailRequest {
  implicit val format = Json.format[SendTemplatedEmailRequest]
}
