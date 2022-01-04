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

import helpers.UnitSpec
import models.{CompanyContactDetails, Links}
import play.api.libs.json.Json
import play.api.test.FakeRequest


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

      val amendedContactDetails = CompanyContactDetails(
        Some("afoo@bar.wibble"),
        Some("012345"),
        Some("012345"),
        Links(Some("link"))
      )

      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", "foo@bar.wibble", amendedContactDetails
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


      val amendedContactDetails = CompanyContactDetails(Some("afoo@bar.wibble"),None, None,
        Links(Some("link"))
      )

      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", "foo@bar.wibble", amendedContactDetails
      )

      val json = Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites)
      json shouldBe expected
    }
    "output json when email is not supplied by the user" in {
      val expected =
        Json.parse("""
                     |{
                     | "externalUserId":"extID",
                     | "authProviderId":"credID",
                     | "journeyId":"regID",
                     | "businessContactDetails":{
                     |   "originalEmail":"foo@bar.wibble"
                     | }
                     |}
                     |""".stripMargin)


      val amendedContactDetails = CompanyContactDetails(None,None, None,
        Links(Some("link"))
      )

      val detail = ContactDetailsAuditEventDetail(
        "extID", "regID", "credID", "foo@bar.wibble", amendedContactDetails
      )

      val json = Json.toJson(detail)(ContactDetailsAuditEvent.auditWrites)
      json shouldBe expected
    }
  }
}