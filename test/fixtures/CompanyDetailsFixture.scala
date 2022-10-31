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

package fixtures

import config.LangConstants
import models._
import models.handoff.{CompanyNameHandOffIncoming, HandoffPPOB}
import play.api.libs.json.{JsObject, Json}

trait CompanyDetailsFixture {

  lazy val validCompanyNameHandOffIncoming =
    CompanyNameHandOffIncoming(
      Some("RegID"),
      "FAKE_OPEN_CONNECT",
      "TestCompanyName",
      CHROAddress(
        "Premises",
        "Line 1,",
        Some("Line 2,"),
        "Locality",
        "Country",
        Some(""),
        Some("FX1 1ZZ"),
        Some("")
      ),
      "testJurisdiction",
      "testTxID",
      Json.parse("""{"ch" : 1}""").as[JsObject],
      Json.parse("""{"hmrc" : 1}""").as[JsObject],
      LangConstants.english,
      Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject]
    )

  lazy val ho2CompanyDetailsResponse = CompanyDetails(
    "ho2-name",
    CHROAddress(
      "ho-2", "ho-2", Some("ho-2"), "Country","ho-2", None, None, None
    ),
    PPOB(
      "MANUAL", Some(Address(Some("1"), "line1", "line2", Some("line3"), Some("line14"), Some("FX1 1ZZ"), None))
    ),
    "testJurisdiction"
  )

  lazy val ho2UpdatedRequest = CompanyDetails(
    "TestCompanyName",
    CHROAddress(
      "Premises", "Line 1,", Some("Line 2,"), "Locality","Country", Some(""), Some("FX1 1ZZ"), Some("")
    ),
    ho2CompanyDetailsResponse.pPOBAddress,
    "testJurisdiction"
  )


  lazy val validCompanyDetailsRequest = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("FX1 1ZZ"), Some("Region")
    ),
    PPOB(
      "", None
    ),
    ""
  )

  lazy val validCompanyDetailsResponse = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("FX1 1ZZ"), Some("Region")
    ),
    PPOB(
      "RO", None
    ),
    "testJurisdiction"
  )

  lazy val validCompanyDetailsLookUpResponse = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("FX1 1ZZ"), Some("Region")
    ),
    PPOB(
      "LOOKUP", Some(Address(Some("1"), "line1", "line2", Some("line3"), Some("line14"), Some("FX1 1ZZ"), Some("UK"), Some("uprn-12345"), "txid-012345"))
    ),
    "testJurisdiction"
  )

  lazy val validCompanyDetailsResponseDifferentAddresses = CompanyDetails(
    "testCompanyName",
    CHROAddress(
      "Premises", "Line1", Some("Line2"), "Locality", "Country",Some("PO Box"), Some("Post Code"), Some("Region")
    ),
    PPOB(
      "MANUAL",Some(Address(Some("1"), "line1", "line2", Some("line3"), Some("line14"), Some("FX1 1ZZ")))
    ),
    "testJurisdiction"
  )

  lazy val handoffPpob1 = HandoffPPOB("line1", "line2", Some("line3"), Some("line14"), Some("FX1 1ZZ"), None)

  lazy val companyDetailsRequestFormData =  Seq(
    "companyName" -> "testCompanyName",
    "chROAddress.premises" -> "Premises",
    "chROAddress.address_line_1" -> "Address line 1",
    "chROAddress.address_line_2" -> "Address line 2",
    "chROAddress.country" -> "Country",
    "chROAddress.locality" -> "Locality",
    "chROAddress.po_box" -> "PO box",
    "chROAddress.postal_code" -> "FX1 1ZZ",
    "chROAddress.region" -> "Region",
    "rOAddress.houseNameNumber" -> "houseNameNumber",
    "rOAddress.addressLine1" -> "addressLine1",
    "rOAddress.addressLine2" -> "addressLine2",
    "rOAddress.addressLine3" -> "addressLine3",
    "rOAddress.addressLine4" -> "addressLine4",
    "rOAddress.postCode" -> "FX1 1ZZ",
    "rOAddress.country" -> "country",
    "pPOBAddress.addressLine1" -> "addressLine1",
    "pPOBAddress.addressLine2" -> "addressLine2",
    "pPOBAddress.addressLine3" -> "addressLine3",
    "pPOBAddress.addressLine4" -> "addressLine4",
    "pPOBAddress.postCode" -> "FX1 1ZZ",
    "pPOBAddress.country" -> "country"
  )
}
