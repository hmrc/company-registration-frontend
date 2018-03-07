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

package services

import address.client.{AddressRecord, Country, Address => LookupAddress}
import fixtures.AddressLookupFixture
import helpers.SCRSSpec
import models.{Address => HMRCAddress}
import uk.gov.hmrc.http.HttpReads

class AddressConverterSpec extends SCRSSpec with AddressLookupFixture {

  class Setup {
    val converter = new AddressConverter {}
  }

  implicit  val reads = HttpReads

  "Converting an address from lookup to HMRC format" should {
    val country: Country = Country("GB", "United Kingdom")
    "address with lines 1 to 3" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1", "line2", "line3"), Some("town"), "FX1 1ZZ", country), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "line2", Some("line3"), Some("town"), Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "address with lines 1, 2 and town" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1", "line2"), Some("town"), "FX1 1ZZ", country ), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "line2", Some("town"), None, Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "address with lines 1 and town" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1"), Some("town"), "FX1 1ZZ", country ), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "town", None, None, Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "address with lines 2 and town" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("", "line1", ""), Some("town"), "FX1 1ZZ", country ), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "town", None, None, Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "address with lines 1 to 5" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1", "line2", "line3", "line4", "line5"), Some("town"), "FX1 1ZZ", country), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "line2", Some("line3"), Some("town"), Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "trim an address where town is the 4th line and is longer than 18 chars" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1", "line2", "line3"), Some("townLongerThan18Chars"), "FX1 1ZZ", country), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "line2", Some("line3"), Some("townLongerThan18Ch"), Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }

    "trim an address where address line 4 is the 4th line of address and is longer than 18 chars" in new Setup {
      val lookedUp = AddressRecord("xxx", LookupAddress(Seq("line1", "line2", "line3", "line4LongerThan18chars"), None, "FX1 1ZZ", country), "en")
      val hmrcAddress = converter.convertLookupAddressToHMRCFormat(lookedUp)
      val expected = HMRCAddress(None, "line1", "line2", Some("line3"), Some("line4LongerThan18c"), Some("FX1 1ZZ"), Some(country.name), Some("xxx"), hmrcAddress.txid)
      hmrcAddress shouldBe expected
    }
  }

  "convert manual address to HMRC format" should {
    "return an address model with all fields populated bar the houseNameNumber" in new Setup {
      val testData = Some(HMRCAddress(Some("12"), "testStreet", "testTown", Some("testRegion"), Some("testCounty"), Some("testPostCode"), Some("testCountry"), txid = "txid"))
      val expected = Some(HMRCAddress(None, "12 testStreet", "testTown", Some("testRegion"), Some("testCounty"), Some("testPostCode"), Some("testCountry"), txid = "txid"))
      val result = converter.convertManualAddressToHMRCFormat(testData)

      result shouldBe expected
    }

    "return an address model with all fields populated bar the houseNameNumber AND L4" in new Setup {
      val testData = Some(HMRCAddress(Some("12"), "testStreetButQuiteRatherDefinitelyLong", "testTown", Some("testRegion"), Some("testCounty"), Some("testPostCode"), Some("testCountry"), txid = "txid"))
      val expected = Some(HMRCAddress(None, "12", "testStreetButQuiteRatherDefinitelyLong", Some("testTown"), Some("testRegion"), Some("testPostCode"), Some("testCountry"), txid = "txid"))

      val result = converter.convertManualAddressToHMRCFormat(testData)

      result shouldBe expected
    }

    "return a None if a none is passed" in new Setup {
      val result = converter.convertManualAddressToHMRCFormat(None)
      result shouldBe None
    }
  }

}
