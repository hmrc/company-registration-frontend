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

import models.{CompanyContactDetails, Links}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class ContactDetailsAuditEventSpec extends UnitSpec {

  implicit val req = FakeRequest("GET", "/test-path")

  "ContactDetailsAuditEventDetail" should {

    "output json in the correct format when 2 full contact detail models are provided" in {
      val expected =
        Json.parse("""
           |{
           | "externalUserId":"extID",
           | "authProviderId":"credID",
           | "journeyId":"regID",
           | "businessContactDetails":{
           |   "originalEmail":"foo@bar.wibble",
           |   "submittedEmail":"afoo@bar.wibble"
           | }
           |}
           |""".stripMargin)

      val contactDetails = CompanyContactDetails(
        Some("firstName"),
        Some("middleName"),
        Some("lastName"),
        Some("012345"),
        Some("012345"),
        Some("foo@bar.wibble"),
        Links(Some("link"))
      )

      val amendedContactDetails = CompanyContactDetails(
        Some("afirstName"),
        Some("amiddleName"),
        Some("alastName"),
        Some("012345"),
        Some("012345"),
        Some("afoo@bar.wibble"),
        Links(Some("link"))
      )

      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", contactDetails, amendedContactDetails
      )

      val json = Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites)
      json shouldBe expected
    }

    "output json in the correct format when all fields that should be in audit event are None" in {
      val expected =
        Json.parse("""
                     |{
                     | "externalUserId":"extID",
                     | "authProviderId":"credID",
                     | "journeyId":"regID",
                     | "businessContactDetails":{}
                     |}
                     |""".stripMargin)

      val contactDetails = CompanyContactDetails(
        Some("foo"), Some("bar"),Some("wizz"), None, None, None,
        Links(Some("link"))
      )

      val amendedContactDetails = CompanyContactDetails(
        Some("foo1"), Some("bar2"),Some("wizz3"), None, None, None,
        Links(Some("link"))
      )


      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", contactDetails, amendedContactDetails
      )

      val json = Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites)
      json shouldBe expected
    }

    "output json in the correct format when only email is supplied" in {
      val expected =
        Json.parse("""
                     |{
                     | "externalUserId":"extID",
                     | "authProviderId":"credID",
                     | "journeyId":"regID",
                     | "businessContactDetails":{
                     |   "originalEmail":"foo@bar.wibble",
                     |   "submittedEmail":"afoo@bar.wibble"
                     | }
                     |}
                     |""".stripMargin)

      val contactDetails = CompanyContactDetails(
        None, None, None, None, None, Some("foo@bar.wibble"),
        Links(Some("link"))
      )

      val amendedContactDetails = CompanyContactDetails(
        None, None, None, None, None, Some("afoo@bar.wibble"),
        Links(Some("link"))
      )

      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", contactDetails, amendedContactDetails
      )

      val json = Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites)
      json shouldBe expected
    }
  }
}
