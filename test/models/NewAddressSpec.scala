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

package models

import fixtures.CorporationTaxFixture
import helpers.JsonValidation
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, Json}
import uk.gov.hmrc.play.test.UnitSpec

class NewAddressSpec extends UnitSpec with CorporationTaxFixture with JsonValidation {


  val roWithCountry = Json.parse(
    """
      |{
      |  "companyDetails":{
      |    "pPOBAddress":{
      |      "address":{
      |        "addressLine1":"10 Test Street",
      |        "addressLine2":"Testtown",
      |        "country":"United Kingdom"
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

  val roWithoutCountry = Json.parse(
    """
      |{
      |  "companyDetails":{
      |    "pPOBAddress":{
      |      "address":{
      |        "addressLine1":"10 Test Street",
      |        "addressLine2":"Testtown",
      |        "postCode":"FX1 1ZZ"
      |      }
      |    }
      |  }
      |}
    """.stripMargin)

  "mkString" should {
    "return an address handling options" in {
      val address = NewAddress("10 Test Street", "Testtown", Some("testshire"), Some("testlevania"), None, None)

      address.mkString shouldBe "10 Test Street, Testtown, testshire, testlevania"
    }
  }

  "isEqualTo" should {

    def buildAddress(aL1: String, postcode: Option[String], country: Option[String]) = NewAddress(
      aL1,
      "testAddressLine2",
      None,
      None,
      postcode,
      country,
      None
    )

    val addressLine1 = "testAddressLine1"
    val otherAddressLine1 = "otherAddressLine1"
    val postcode = "FX1 1ZZ"
    val country = "testCountry"

    val address = buildAddress(addressLine1, Some(postcode), Some(country))
    val addressWithNoPostcodeOrCountry = buildAddress(addressLine1, None, None)
    val addressWithCountryButNoPostcode = buildAddress(addressLine1, None, Some(country))

    val addressWithDiffAddressLine1 = buildAddress(otherAddressLine1, Some(postcode), Some(country))
    val addressWithDiffPostcode = buildAddress(otherAddressLine1, Some("otherPostcode"), Some(country))
    val addressWithDiffCountry = buildAddress(otherAddressLine1, None, Some("otherCountry"))

    "return true" when {

      "address line 1 on both addresses match and postcodes match" in {
        assertResult(true)(address isEqualTo address)
      }

      "address line 1 matches but postcode on both addresses are not supplied and countries match" in {
        assertResult(true)(addressWithCountryButNoPostcode isEqualTo addressWithCountryButNoPostcode)
      }
    }

    "return false" when {

      "addressLine1 doesn't match" in {
        assertResult(false)(address isEqualTo addressWithDiffAddressLine1)
      }

      "address line 1 on both addresses match but postcode doesn't exist on 1 and countries match" in {
        assertResult(false)(address isEqualTo addressWithCountryButNoPostcode)
      }

      "addressLine1 matches but postcodes do not" in {
        assertResult(false)(address isEqualTo addressWithDiffPostcode)
      }

      "addressLine1 matches but postcode is empty and country does not match" in {
        assertResult(false)(address isEqualTo addressWithDiffCountry)
      }

      "addressLine1 matches but postcode and country are missing" in {
        assertResult(false)(address isEqualTo addressWithNoPostcodeOrCountry)
      }
    }
  }

  "companyRegistrationFormats" should {

    val ctRegJson = buildCorporationTaxModel()

    "be able to read a company registration document into an Address model" in {
      val result1 = Json.fromJson(ctRegJson)(NewAddress.ppobFormats)
      val expected1 = NewAddress("10 Test Street", "Testtown", None, None, Some("FX1 1ZZ"), Some("United Kingdom"), None)

      result1.get shouldBe expected1


      val ro = Json.parse(s"""
                        |{
                        | "companyDetails": {
                        |   "cHROAddress" : {
                        |     "premises" : "This is about 27 characters",
                        |     "address_line_1" : "test road",
                        |     "address_line_2" : "test town",
                        |     "country" : "United Kingdom",
                        |     "locality" : "Lawley"
                        |   }
                        | }
                        |}""".stripMargin)

      val result2 = Json.fromJson(ro)(NewAddress.roReads)
      val expected2 = NewAddress("This is about 27 characters", "test road", Some("test town"), Some("Lawley"), None, Some("United Kingdom"), None)

      result2.get shouldBe expected2
    }

    "be able to write an Address as json correctly" in {
      val address1 = NewAddress("10 Test Street", "Testtown", None, None, Some("FX1 1ZZ"), None)
      val result1 = Json.toJson(address1)(NewAddress.ppobFormats)
      result1 shouldBe roWithoutCountry


      val address2 = NewAddress("10 Test Street", "Testtown", None, None, None, Some("United Kingdom"))
      val result2 = Json.toJson(address2)(NewAddress.ppobFormats)

      result2 shouldBe roWithCountry
    }

    "error if there are not two address lines" in {
      val address = Json.parse(
        s"""
           |{
           |  "companyDetails" : {
           |    "cHROAddress" : {
           |      "premises": "14",
           |      "country": "UK",
           |      "postal_code": "FX1 1ZZ"
           |    }
           |  }
           |}
       """.stripMargin)
      val result = Json.fromJson(address)(NewAddress.roReads)

      val errorMsg =
        "Only 1 address lines returned for RO Address\n" +
        "Lines defined:\n" +
        "premises: true\n" +
        "address line 1: false\n" +
        "address line 2: false\n" +
        "locality: false\n" +
        "postcode: true\n" +
        "country: true"

      shouldHaveErrors(result, JsPath(), Seq(ValidationError(errorMsg)))
    }

    "error if there is no postcode or country" in {
      val address = Json.parse(
        s"""
           |{
           |  "companyDetails" : {
           |    "cHROAddress" : {
           |      "premises": "14",
           |      "address_line_1": "test road",
           |      "address_line_2": "test town",
           |      "locality": "Foo"
           |    }
           |  }
           |}
       """.stripMargin)
      val result = Json.fromJson(address)(NewAddress.roReads)


      val errorMsg =
        "Neither postcode nor country returned for RO Address\n" +
          "Lines defined:\n" +
          "premises: true\n" +
          "address line 1: true\n" +
          "address line 2: true\n" +
          "locality: true\n" +
          "postcode: false\n" +
          "country: false"

      shouldHaveErrors(result, JsPath(), Seq(ValidationError(errorMsg)))
    }
  }


  "addressLookupReads" should {

    "be able to successfully read a looked up address into an Address model" when {

      "all lines are provided" in {
        val addressLookupJson = Json.parse(
          """{
            |  "auditRef":"tstAuditRef",
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford",
            |      "Testley",
            |      "Testshire"
            |    ],
            |    "postcode":"FX1 1ZZ",
            |    "country":{
            |      "code":"UK",
            |      "name":"United Kingdom"
            |    }
            |  }
            |}""".stripMargin)

        val result = Json.fromJson(addressLookupJson)(NewAddress.addressLookupReads)

        val expected = NewAddress("Address Line 1", "Testford", Some("Testley"), Some("Testshire"), Some("FX1 1ZZ"), None, Some("tstAuditRef"))

        result.get shouldBe expected
      }

      "three lines are defined" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford",
            |      "Testley"
            |    ],
            |    "postcode":"FX1 1ZZ",
            |    "country":{
            |      "code":"UK",
            |      "name":"United Kingdom"
            |    }
            |  }
            |}""".stripMargin)

        val res = NewAddress("Address Line 1", "Testford", Some("Testley"), None, Some("FX1 1ZZ"), None, None)

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe res
      }

      "two lines are defined" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ],
            |    "postcode":"FX1 1ZZ",
            |    "country":{
            |      "code":"UK",
            |      "name":"United Kingdom"
            |    }
            |  }
            |}""".stripMargin)

        val res = NewAddress("Address Line 1", "Testford", None, None, Some("FX1 1ZZ"), None, None)

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe res
      }

      "the postcode is not provided but a country is" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ],
            |    "country":{
            |      "code":"UK",
            |      "name":"United Kingdom"
            |    }
            |  }
            |}""".stripMargin)

        val res = NewAddress("Address Line 1", "Testford", None, None, None, Some("United Kingdom"), None)

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe res
      }

      "the country is not provided but a postcode is" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ],
            |    "postcode":"FX1 1ZZ"
            |  }
            |}""".stripMargin)

        val res = NewAddress("Address Line 1", "Testford", None, None, Some("FX1 1ZZ"), None, None)

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe res
      }

      "a postcode is invalid but country is provided" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ],
            |    "postcode":"Inval!d P0STCODE",
            |    "country":{
            |      "code":"UK",
            |      "name":"United Kingdom"
            |    }
            |  }
            |}""".stripMargin)

        val res = NewAddress("Address Line 1", "Testford", None, None, None, Some("United Kingdom"), None)

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe res
      }

      "lines 1-4 are too long and are there trimmed" in {
        val json = Json.parse(
          """
            |{
            | "address" : {
            |   "lines": [
            |     "abcdefghijklmnopqrstuvwxyz@#",
            |     "abcdefghijklmnopqrstuvwxyz@#",
            |     "abcdefghijklmnopqrstuvwxyz@#",
            |     "abcdefghijklmnopqrstuvwxyz@#"
            |   ],
            |   "postcode" : "FX1 1ZZ",
            |   "country" : {
            |     "code" : "UK",
            |     "name" : "United Kingdom"
            |   }
            | }
            |}
          """.stripMargin)

        val expected = NewAddress(
          addressLine1 = "abcdefghijklmnopqrstuvwxyz@",
          addressLine2 = "abcdefghijklmnopqrstuvwxyz@",
          addressLine3 = Some("abcdefghijklmnopqrstuvwxyz@"),
          addressLine4 = Some("abcdefghijklmnopqr"),
          postcode = Some("FX1 1ZZ"),
          country = None
        )

        Json.fromJson(json)(NewAddress.addressLookupReads).get shouldBe expected
      }
    }

    "fail reading an Address Lookup model" when {

      "neither postcode nor country are completed" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ]
            |  }
            |}""".stripMargin)

        val result = Json.fromJson(json)(NewAddress.addressLookupReads)

        shouldHaveErrors(result, JsPath(), Seq(ValidationError("no country or valid postcode")))
      }

      "only one address line is provided" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1"
            |    ],
            |    "postcode":"FX1 1ZZ"
            |  }
            |}""".stripMargin)

        val result = Json.fromJson(json)(NewAddress.addressLookupReads)
        shouldHaveErrors(result, JsPath(), Seq(ValidationError("only 1 lines provided from address-lookup-frontend")))
      }

      "postcode is invalid and country is not provided" in {
        val json = Json.parse(
          """{
            |  "address":{
            |    "lines":[
            |      "Address Line 1",
            |      "Testford"
            |    ],
            |    "postcode":"Inval!d P0STCODE"
            |  }
            |}""".stripMargin)

        val result = Json.fromJson(json)(NewAddress.addressLookupReads)
        shouldHaveErrors(result, JsPath(), Seq(ValidationError("no country or valid postcode")))
      }
    }
  }
}
