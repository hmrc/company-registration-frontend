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

import audit.TransactionNames.AMEND_PRE_POP_CONTACT_DETAILS
import audit.events.RegistrationAuditEvent.{AUTH_PROVIDER_ID, EXT_USER_ID, JOURNEY_ID}
import models.CompanyContactDetails
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

case class ContactDetailsAuditEventDetail(externalUserId: String,
                                          regId: String,
                                          credId: String,
                                          originalEmail: String,
                                          amendedContactDetails: CompanyContactDetails)

object ContactDetailsAuditEvent {

  val auditWrites = new Writes[ContactDetailsAuditEventDetail] {
    def writes(detail: ContactDetailsAuditEventDetail) = {
      val userSubmittedEmail = detail.amendedContactDetails.contactEmail.fold(Json.obj())(
        e => Json.obj("submittedEmail" -> e))
      Json.obj(
        EXT_USER_ID -> detail.externalUserId,
        AUTH_PROVIDER_ID -> detail.credId,
        JOURNEY_ID -> detail.regId,
        "businessContactDetails" -> Json.obj(
            "originalEmail" -> Some(detail.originalEmail)).deepMerge(userSubmittedEmail)
      )
    }
  }
}

class ContactDetailsAuditEvent(detail : ContactDetailsAuditEventDetail)(implicit hc : HeaderCarrier, req: Request[AnyContent])
  extends RegistrationAuditEvent(
    AMEND_PRE_POP_CONTACT_DETAILS,
    Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites).as[JsObject]
  )
