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

package audit.events

import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier

case class EmailMismatchEventDetail(externalUserId : String,
                                    authProviderId : String,
                                    journeyId : String)

object EmailMismatchEventDetail {
  implicit val format = Json.format[EmailMismatchEventDetail]
}

class EmailMismatchEvent(detail : EmailMismatchEventDetail)(implicit hc : HeaderCarrier, req: Request[AnyContent])
  extends RegistrationAuditEvent(
    "emailMismatch",
    Json.toJson(detail).as[JsObject]
  )(hc, req)
