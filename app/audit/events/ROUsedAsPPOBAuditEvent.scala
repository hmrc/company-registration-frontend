/*
 * Copyright 2021 HM Revenue & Customs
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

import models.CHROAddress
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

case class ROUsedAsPPOBAuditEventDetail(regId: String,
                                        credId: String,
                                        companyName: String,
                                        address: CHROAddress)


object ROUsedAsPPOBAuditEventDetail {
  import RegistrationAuditEvent.{AUTH_PROVIDER_ID, COMPANY_NAME, JOURNEY_ID, RO_ADDRESS}
  implicit val writes = new Writes[ROUsedAsPPOBAuditEventDetail] {
    def writes(detail: ROUsedAsPPOBAuditEventDetail) = {
      Json.obj(
        AUTH_PROVIDER_ID -> detail.credId,
        JOURNEY_ID -> detail.regId,
        COMPANY_NAME -> detail.companyName,
        RO_ADDRESS -> Json.toJson(detail.address)(CHROAddress.auditWrites)
      )
    }
  }
}

class ROUsedAsPPOBAuditEvent(details: ROUsedAsPPOBAuditEventDetail)(implicit hc: HeaderCarrier, req: Request[AnyContent])
  extends RegistrationAuditEvent(
    "registeredOfficeUsedAsPrincipalPlaceOfBusiness",
    Json.toJson(details).as[JsObject]
  )(hc, req)
