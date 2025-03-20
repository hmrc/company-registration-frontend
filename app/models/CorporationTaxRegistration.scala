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

package models

import models.connectors.ConfirmationReferences
import play.api.libs.json.{Json, OFormat}

case class CorporationTaxRegistrationRequest(language: String)

object CorporationTaxRegistrationRequest {
  implicit val formats: OFormat[CorporationTaxRegistrationRequest] = Json.format[CorporationTaxRegistrationRequest]
}


case class CorporationTaxRegistrationResponse(registrationID: String,
                                              formCreationTimestamp: String)

object CorporationTaxRegistrationResponse {
  implicit val formats: OFormat[CorporationTaxRegistrationResponse] = Json.format[CorporationTaxRegistrationResponse]
}

sealed trait ConfirmationReferencesResponse
final case class ConfirmationReferencesSuccessResponse(ref: ConfirmationReferences) extends ConfirmationReferencesResponse
case object ConfirmationReferencesNotFoundResponse extends ConfirmationReferencesResponse
case object ConfirmationReferencesBadRequestResponse extends ConfirmationReferencesResponse
case object ConfirmationReferencesErrorResponse extends ConfirmationReferencesResponse
case object DESFailureDeskpro extends ConfirmationReferencesResponse
case object DESFailureRetriable extends ConfirmationReferencesResponse
