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

package audit.events

import audit.events.AuditEventConstants.{AUTH_PROVIDER_ID, EXT_USER_ID, JOURNEY_ID}
import models.CompanyContactDetails
import play.api.libs.json.{Json, Writes}

case class ContactDetailsAuditEventDetail(externalUserId: String,
                                          regId: String,
                                          credId: String,
                                          originalEmail: String,
                                          amendedContactDetails: CompanyContactDetails)

object ContactDetailsAuditEventDetail {
  implicit val writes: Writes[ContactDetailsAuditEventDetail] = Writes[ContactDetailsAuditEventDetail] { detail =>
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
