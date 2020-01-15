/*
 * Copyright 2020 HM Revenue & Customs
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

package forms

import helpers.FormTestHelpers
import uk.gov.hmrc.play.test.UnitSpec

class CompanyDetailsFormSpec extends UnitSpec with FormTestHelpers {

  "Company details form" should {

    val form = CompanyDetailsForm.form

    "not contain any errors for a full valid data" in {
      val data = Map(
        "companyName" -> "testCompanyName",
        "chROAddress.premises" -> "Premises",
        "chROAddress.address_line_1" -> "Address line 1",
        "chROAddress.address_line_2" -> "Address line 2",
        "chROAddress.country" -> "Country",
        "chROAddress.locality" -> "Locality",
        "chROAddress.po_box" -> "PO box",
        "chROAddress.postal_code" -> "Post code",
        "chROAddress.region" -> "Region",
        "pPOBAddress.address.houseNameNumber" -> "houseNameNumber",
        "pPOBAddress.address.addressLine1" -> "addressLine1",
        "pPOBAddress.address.addressLine2" -> "addressLine2",
        "pPOBAddress.address.addressLine3" -> "addressLine3",
        "pPOBAddress.address.addressLine4" -> "addressLine4",
        "pPOBAddress.address.postCode" -> "postcode",
        "pPOBAddress.address.country" -> "country",
        "pPOBAddress.address.uprn" -> "012345",
        "jurisdiction" -> "testJurisdiction"
      )

      assertFormSuccess(form, data)
    }

    "not contain any errors when a PPOB address is not provided" in {
      val data = Map(
        "companyName" -> "testCompanyName",
        "chROAddress.premises" -> "Premises",
        "chROAddress.address_line_1" -> "Address line 1",
        "chROAddress.country" -> "Country",
        "chROAddress.locality" -> "Locality",
        "jurisdiction" -> "testJurisdiction"
      )

      assertFormSuccess(form, data)
    }
  }
}
