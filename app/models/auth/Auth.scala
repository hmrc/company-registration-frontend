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

package models.auth

import play.api.libs.json.{Json, OFormat}

case class EnrolmentIdentifier(key: String, value: String)

object EnrolmentIdentifier {
  implicit val format: OFormat[EnrolmentIdentifier] = Json.format[EnrolmentIdentifier]
}

case class Enrolment(key: String,
                     identifiers: Seq[EnrolmentIdentifier],
                     state: String)

object Enrolment {
  implicit val format: OFormat[Enrolment] = Json.format[Enrolment]
}
