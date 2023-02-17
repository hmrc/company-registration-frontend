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

import helpers.UnitSpec
import play.api.libs.json.Json

class CompanyDetailsModelsSpec extends UnitSpec {

  "CompanyDetailsRequest" should {
    "successfully update from Handoff" in {
      val ch = CHROAddress("1", "2", None, "3", "4", None, None, None)
      lazy val simpleRoAddress = CHROAddress("","",Some(""),"","",Some(""),Some(""),Some(""))
      val current = CompanyDetails("TestName", simpleRoAddress, PPOB.empty, "")
      val updated = CompanyDetails.updateFromHandoff(current, "New Name For Test", ch, "new")

      updated mustBe CompanyDetails("New Name For Test", ch, PPOB.empty, "new")
    }
  }

  "CHROAddress" should {

    "write to json" in {
      val chAddressComplete = CHROAddress(
        "testPremises",
        "testAdrrLine1",
        Some("testAdrrLine1"),
        "testLocality",
        "testCountry",
        Some("testPOBox"),
        Some("testPostalCode"),
        Some("testRegion")
      )

      val jsonComplete = Json.parse(
        """
          |{
          | "premises":"testPremises",
          | "address_line_1":"testAdrrLine1",
          | "address_line_2":"testAdrrLine1",
          | "locality":"testLocality",
          | "country":"testCountry",
          | "po_box":"testPOBox",
          | "postal_code":"testPostalCode",
          | "region":"testRegion"
          | }""".stripMargin)

      Json.toJson(chAddressComplete) mustBe jsonComplete
    }

    "read from json when option fields are missing" in {
      val jsonPartial = Json.parse(
        """
          |{
          | "premises":"testPremises",
          | "address_line_1":"testAdrrLine1",
          | "locality":"testLocality",
          | "country":"testCountry"
          | }""".stripMargin)

      val chAddressPartial = CHROAddress(
        "testPremises",
        "testAdrrLine1",
        None,
        "testLocality",
        "testCountry",
        None,
        None,
        None
      )

      Json.fromJson[CHROAddress](jsonPartial).get mustBe chAddressPartial
    }
  }

  "Address" should {

    "write to the correct json structure when using prePopWrites" when {

      "only line 3 and line 4 are not supplied" in {
        val address = Address(
          Some("15"),
          "line1",
          "line2",
          None,
          None,
          Some("FX1 1ZZ"),
          Some("UK"),
          None,
          "test-txid"
        )

        val expected = Json.parse(
          """
            |{
            |  "addressLine1":"line1",
            |  "postcode":"FX1 1ZZ",
            |  "txid":"test-txid",
            |  "country":"UK",
            |  "addressLine2":"line2"
            |}
          """.stripMargin)

        val prePopAddressJson = Json.toJson(address)(Address.prePopWrites)

        prePopAddressJson mustBe expected
      }

      "line 1 and line 2 together are longer than 26 chars" in {
        val address = Address(
          Some(""),
          "1234567890123456abcdefghi27",
          "line2",
          Some("line3"),
          Some("line4"),
          Some("FX1 1ZZ"),
          Some("UK"),
          None,
          "test-txid"
        )

        val expected = Json.parse(
          """
            |{
            |  "addressLine1":"1234567890123456abcdefghi27",
            |  "addressLine2":"line2",
            |  "addressLine3":"line3",
            |  "addressLine4":"line4",
            |  "postcode":"FX1 1ZZ",
            |  "country":"UK",
            |  "txid":"test-txid"
            |}
          """.stripMargin)

        val prePopAddressJson = Json.toJson(address)(Address.prePopWrites)

        prePopAddressJson mustBe expected
      }

      "line 1 and line 2 together are shorter than 26 chars" in {
        val address = Address(
          Some(""),
          "1234567890123456abcdefg25",
          "line2",
          Some("line3"),
          Some("line4"),
          Some("FX1 1ZZ"),
          Some("UK"),
          None,
          "test-txid"
        )

        val expected = Json.parse(
          """
            |{
            |  "addressLine1":"1234567890123456abcdefg25",
            |  "addressLine2":"line2",
            |  "addressLine3":"line3",
            |  "addressLine4":"line4",
            |  "postcode":"FX1 1ZZ",
            |  "country":"UK",
            |  "txid":"test-txid"
            |}
          """.stripMargin)

        val prePopAddressJson = Json.toJson(address)(Address.prePopWrites)

        prePopAddressJson mustBe expected
      }
    }
  }
}
