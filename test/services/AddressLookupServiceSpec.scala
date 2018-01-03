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

import fixtures.AddressLookupFixture
import helpers.SCRSSpec
import address.client.RecordSet
import play.api.libs.json.{JsResultException}
import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads }

class AddressLookupServiceSpec extends SCRSSpec with AddressLookupFixture {

  class Setup {
    val service = new AddressLookupService {
      override val http = mockWSHttp
      override val addressLookupUrl = "testAddressLookupUrl"
    }
  }

  val url = "http://test.url"

  implicit val hcExtraHeader = HeaderCarrier().withExtraHeaders("X-Hmrc-Origin" -> "SCRS")
  implicit  val reads = HttpReads

  "Calling lookup" should {
    "return a successful address lookup response" when {
      "a postcode and filter are provided" in new Setup {
        mockHttpGet(url, addressJsonList)
        await(service.lookup("testPostcode", Some("testFilter"))) shouldBe AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(addressJsonList))
      }

      "a postcode and filter are provided but the address isn't found" in new Setup {
        mockHttpGet(url, emptyAddressJson)
        await(service.lookup("testPostcode", Some("testFilter"))) shouldBe AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(emptyAddressJson))
      }

      "only a postcode is provided" in new Setup {
        mockHttpGet(url, addressJsonList)
        await(service.lookup("testPostcode", None)) shouldBe AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(addressJsonList))
      }
    }

    "return an unsuccessful address lookup response" when {
      "a postcode and filter are provided but the Json is invalid" in new Setup {
        mockHttpGet(url, invalidAddressJson)
        val expected = await(service.lookup("testPostcode", None))

        expected match {
          case AddressLookupErrorResponse(e:JsResultException) => {
            e.errors.length shouldBe 1
            val (key, errors) = e.errors.head
            key.toString shouldBe "(0)/address/country/name"
            errors.length shouldBe 1
            errors.head.message shouldBe "error.path.missing"
          }
          case _ => fail("Did not match as an AddressLookupErrorResponse with a JsResultException")
        }
      }
    }
  }

  "checkFilter" should {

    "return a blank string when an empty option is supplied" in new Setup {
      service.checkFilter(None) shouldBe ""
    }

    "return a string prefixed with &filter= if a filter is supplied" in new Setup {
      val filter = Some("test")
      service.checkFilter(filter) shouldBe "&filter=test"
    }

    "return a string containing a + symbol when a 2 string filter separated by a space is supplied" in new Setup {
      val filter = Some("14 test")
      service.checkFilter(filter) shouldBe "&filter=14+test"
    }

    "return a lookup ready string when more than a 2 string filter separated by a space is supplied" in new Setup {
      val filter = Some("14 test test test street")
      service.checkFilter(filter) shouldBe "&filter=14+test+test+test+street"
    }
  }
}
