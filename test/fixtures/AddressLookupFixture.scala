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

package fixtures

import play.api.libs.json.Json
import services.AddressLookupSuccessResponse
//import uk.gov.hmrc.address.client.v1.{Country, Address, AddressRecord, RecordSet}
import address.client.{Address, AddressRecord, Country, RecordSet}

trait AddressLookupFixture {

  lazy val validRecordSet = RecordSet(
    Seq(AddressRecord(
      id = "testId",
      Address(
        lines = Seq(
          "testLine1",
          "testLine2",
          "testLine3",
          "testLine4"
        ),
        town = Some("testTown"),
        postcode = "testPostcode",
        country = Country("UK", "United Kingdom")
      ),
      language = "en"
    ))
  )

  lazy val validAddressLookupFormData = Seq(
    "lookup.postcode" -> "FX1 1ZZ",
    "lookup.houseNameNumber" -> "10"
  )

  lazy val invalidAddressLookupPostcodeFormData = Seq(
    "lookup.postcode" -> "FX1 1$Z",
    "lookup.houseNameNumber" -> "10"
  )

  lazy val emptyAddressLookupFormData = Seq.empty

  lazy val validAddressLookupResponse = AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(addressJsonList))

  lazy val addressJson = Json.parse("""[{
                      |  "id": "GB123456789011",
                      |  "address": {
                      |    "lines": [
                      |      "Flat 1",
                      |      "12-15 A Street",
                      |      "Some Place"
                      |    ],
                      |    "town": "Any Town",
                      |    "postcode": "FX1 1ZZ",
                      |    "country": {
                      |      "code": "GB",
                      |      "name": "UK"
                      |    }
                      |  },
                      |  "language": "en"
                      |}]""".stripMargin)

  lazy val addressJsonList = Json.parse("""[{
                          |  "id": "GB123456789011",
                          |  "address": {
                          |    "lines": [
                          |      "Flat 1",
                          |      "12-15 A Street",
                          |      "Some Place"
                          |    ],
                          |    "town": "Any Town",
                          |    "postcode": "FX1 1ZZ",
                          |    "country": {
                          |      "code": "GB",
                          |      "name": "UK"
                          |    }
                          |  },
                          |  "language": "en"
                          |},
                          |{
                          |  "id": "GB123456123011",
                          |  "address": {
                          |    "lines": [
                          |      "Flat 21",
                          |      "12-115 A Street",
                          |      "Some Place"
                          |    ],
                          |    "town": "Any Town",
                          |    "postcode": "FX1 1ZZ",
                          |    "country": {
                          |      "code": "GB",
                          |      "name": "UK"
                          |    }
                          |  },
                          |  "language": "en"
                          |}]""".stripMargin)

  lazy val emptyAddressJson = Json.parse("""[{
                                      |  "id": "",
                                      |  "address": {
                                      |    "lines": [],
                                      |    "town": "",
                                      |    "postcode": "",
                                      |    "country": {
                                      |      "code": "",
                                      |      "name": ""
                                      |    }
                                      |  },
                                      |  "language": ""
                                      |}]""".stripMargin)

  lazy val invalidAddressJson = Json.parse("""[{
                                           |  "id": "",
                                           |  "address": {
                                           |    "lines": [],
                                           |    "town": "",
                                           |    "postcode": "",
                                           |    "country": {
                                           |      "code": ""
                                           |    }
                                           |  },
                                           |  "language": ""
                                           |}]""".stripMargin)
}
