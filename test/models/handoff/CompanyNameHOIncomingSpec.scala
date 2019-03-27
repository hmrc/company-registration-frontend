/*
 * Copyright 2019 HM Revenue & Customs
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

package models.handoff

import models.{CHROAddress, JsonFormatValidation}
import play.api.data.validation.ValidationError
import play.api.libs.json._
import uk.gov.hmrc.play.test.UnitSpec


class CompanyNameHOIncomingSpec extends UnitSpec with JsonFormatValidation {

  def lineEnd(comma: Boolean) = if (comma) "," else ""

  def jsonLine(key: String, value: String): String = jsonLine(key, value, true)

  def jsonLine(key: String, value: String, comma: Boolean): String = s""""${key}" : "${value}"${lineEnd(comma)}"""

  def jsonLineRaw(key: String, value: String, comma: Boolean = true): String = s""""${key}" : ${value}${lineEnd(comma)}"""

  def jsonLine(key: String, value: Option[String], comma: Boolean = true): String = value.fold("")(v =>s""""${key}" : "${v}"${lineEnd(comma)}""")

  def inJson(regId: String, userId: String, companyName: String, jurisdiction: String, txid: String, roAddressJson: String = roJson()) = {
    s"""
       |{
       |  ${jsonLine("journey_id", regId)}
       |  ${jsonLine("user_id", userId)}
       |  ${jsonLine("company_name", companyName)}
       |  ${jsonLineRaw("registered_office_address", roAddressJson)}
       |  ${jsonLine("jurisdiction", jurisdiction)}
       |  ${jsonLine("transaction_id", txid)}
       |  "ch": {},
       |  "hmrc": {},
       |  "links": {}
       |}
     """.stripMargin
  }

  def roJson(line1: String = "1", line2: Option[String] = None, country: Option[String] = Some("c")) = {
    s"""
       |{
       |  "premises" : "p",
       |  ${jsonLine("address_line_1", line1)}
       |  ${jsonLine("address_line_2", line2)}
       |  ${jsonLine("country", country)}
       |  "locality" : "l",
       |  "po_box" : "pb",
       |  "postal_code" : "pc",
       |  "region" : "r"
       |}
     """.stripMargin
  }

  "Incoming model" should {
    "Be able to be parsed from basic JSON" in {
      val json = inJson("r", "u", "name", "j", "txid")
      val empty = Json.obj()

      val expected = CompanyNameHandOffIncoming(Some("r"), "u", "name",
        CHROAddress("p", "1", None, "l", "c", Some("pb"), Some("pc"), Some("r")),
        "j", "txid", empty, empty, empty)

      val result = Json.parse(json).validate[CompanyNameHandOffIncoming]

      shouldBeSuccess(expected, result)
    }

    "accept a company name that contains special characters" in {
      val companyName = "Company Name Ltd<>@"
      val json = inJson("r", "u", companyName, "j", "txid")
      val empty = Json.obj()
      val expected = CompanyNameHandOffIncoming(Some("r"), "u", companyName,
        CHROAddress("p", "1", None, "l", "c", Some("pb"), Some("pc"), Some("r")),
        "j", "txid", empty, empty, empty)

      val result = Json.parse(json).validate[CompanyNameHandOffIncoming]

      shouldBeSuccess(expected, result)
    }

    "CHROAddress Model" should {
      "Be able to be parsed from basic JSON" in {
        val line1 = "12345678901234567890123456789012345678901234567890"
        val json = roJson(line1 = line1)
        val expected = CHROAddress("p", line1, None, "l", "c", Some("pb"), Some("pc"), Some("r"))

        val result = Json.parse(json).validate[CHROAddress]

        shouldBeSuccess(expected, result)
      }

      "parse as UK for missing Country" in {
        val line1 = "12345678901234567890123456789012345678901234567890"
        val json = roJson(line1 = line1, country = None)
        val expected = CHROAddress("p", line1, None, "l", "UK", Some("pb"), Some("pc"), Some("r"))

        val result = Json.parse(json).validate[CHROAddress]

        shouldBeSuccess(expected, result)
      }

      "Be able to be parsed with a line 2" in {
        val line2 = Some("12345678901234567890123456789012345678901234567890")
        val json = roJson(line2 = line2)
        val expected = CHROAddress("p", "1", line2, "l", "c", Some("pb"), Some("pc"), Some("r"))

        val result = Json.parse(json).validate[CHROAddress]

        shouldBeSuccess(expected, result)
      }

      "fail to be read from JSON if is empty string" in {
        val json = roJson(line1 = "")

        val result = Json.parse(json).validate[CHROAddress]

        shouldHaveErrors(result, JsPath() \ "address_line_1", ValidationError("error.minLength", 1))
      }

      "fail to be read from JSON if line1 is longer than 50 characters" in {
        val json = roJson(line1 = "123456789012345678901234567890123456789012345678901")

        val result = Json.parse(json).validate[CHROAddress]

        shouldHaveErrors(result, JsPath() \ "address_line_1", ValidationError("error.maxLength", 50))
      }

      "fail to be read from JSON if line2 is empty string" in {
        val json = roJson(line2 = Some(""))

        val result = Json.parse(json).validate[CHROAddress]

        shouldHaveErrors(result, JsPath() \ "address_line_2", ValidationError("error.minLength", 1))
      }

      "fail to be read from JSON if line2 is longer than 50 characters" in {
        val json = roJson(line2 = Some("123456789012345678901234567890123456789012345678901"))

        val result = Json.parse(json).validate[CHROAddress]

        shouldHaveErrors(result, JsPath() \ "address_line_2", ValidationError("error.maxLength", 50))
      }
    }
  }
}