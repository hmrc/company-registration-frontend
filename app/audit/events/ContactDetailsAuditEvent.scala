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

package audit.events

import audit.TransactionNames
import models.CompanyContactDetails
import play.api.libs.json.{Writes, JsObject, Json}
import play.api.mvc.{Request, AnyContent}
import TransactionNames.AMEND_PRE_POP_CONTACT_DETAILS
import RegistrationAuditEvent.{AUTH_PROVIDER_ID, JOURNEY_ID, EXT_USER_ID}
import uk.gov.hmrc.http.HeaderCarrier

case class ContactDetailsAuditEventDetail(externalUserId: String,
                                          regId: String,
                                          credId: String,
                                          ggContactDetails: CompanyContactDetails,
                                          amendedContactDetails: CompanyContactDetails)

object ContactDetailsAuditEvent {
  private def filterOpts(seq: (String, Option[String])*): Seq[(String, Json.JsValueWrapper)] = {
    seq.collect{ case (k, Some(v)) => (k, Json.toJsFieldJsValueWrapper(v)) }
  }

  val auditWrites = new Writes[ContactDetailsAuditEventDetail] {
    def writes(detail: ContactDetailsAuditEventDetail) = {
      Json.obj(
        EXT_USER_ID -> detail.externalUserId,
        AUTH_PROVIDER_ID -> detail.credId,
        JOURNEY_ID -> detail.regId,
        "businessContactDetails" -> Json.obj(
          filterOpts(
            "originalFirstName" -> detail.ggContactDetails.contactFirstName,
            "originalMiddleNames" -> detail.ggContactDetails.contactMiddleName,
            "originalLastName" -> detail.ggContactDetails.contactSurname,
            "originalEmail" -> detail.ggContactDetails.contactEmail,
            "submittedFirstName" -> detail.amendedContactDetails.contactFirstName,
            "submittedMiddleNames" -> detail.amendedContactDetails.contactMiddleName,
            "submittedLastName" -> detail.amendedContactDetails.contactSurname,
            "submittedEmail" -> detail.amendedContactDetails.contactEmail
          ): _*
        )
      )
    }
  }
}

class ContactDetailsAuditEvent(detail : ContactDetailsAuditEventDetail)(implicit hc : HeaderCarrier, req: Request[AnyContent])
  extends RegistrationAuditEvent(
    AMEND_PRE_POP_CONTACT_DETAILS,
    Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites).as[JsObject]
  )(hc, req)
