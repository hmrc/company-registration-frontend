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

package fixtures

import play.api.libs.json.{JsValue, Json}

trait CorporationTaxFixture {

  private lazy val OID = "123456789"
  private lazy val RID = "123456789"
  private lazy val STATUS = "draft"
  private lazy val ADDRESS_TYPE = "RO"

  def buildCorporationTaxModel(oid: String = OID,
                               rid: String = RID,
                               status: String = STATUS,
                               addressType: String = ADDRESS_TYPE): JsValue = {
    Json.parse(
      s"""
         |{
         |    "OID" : "$oid",
         |    "registrationID" : "$rid",
         |    "status" : "$status",
         |    "formCreationTimestamp" : "2016-10-25T12:20:45Z",
         |    "language" : "en",
         |    "verifiedEmail" : {
         |        "address" : "user@test.com",
         |        "type" : "GG",
         |        "link-sent" : true,
         |        "verified" : true,
         |        "return-link-email-sent" : false
         |    },
         |    "confirmationReferences" : {
         |        "acknowledgementReference" : "BRCT123456789",
         |        "transactionId" : "TRANS_ID-123456789",
         |        "paymentReference" : "PAY_REF-123456789",
         |        "paymentAmount" : "12"
         |    },
         |    "companyDetails" : {
         |        "companyName" : "testCompanyname",
         |        "cHROAddress" : {
         |            "premises" : "14",
         |            "address_line_1" : "test road",
         |            "address_line_2" : "test town",
         |            "country" : "UK",
         |            "locality" : "Foo",
         |            "postal_code" : "FX1 1ZZ"
         |        },
         |        "rOAddress" : {
         |            "houseNameNumber" : "14",
         |            "addressLine1" : "test road",
         |            "addressLine2" : "test town",
         |            "addressLine3" : "Foo",
         |            "addressLine4" : "",
         |            "postCode" : "FX1 1ZZ",
         |            "country" : "UK"
         |        },
         |        "pPOBAddress" : {
         |            "addressType" : "$addressType",
         |            "address" : {
         |                "addressLine1" : "10 Test Street",
         |                "addressLine2" : "Testtown",
         |                "postCode" : "FX1 1ZZ",
         |                "country" : "United Kingdom",
         |                "uprn" : "GB123491234512",
         |                "txid" : "123-123-123-123-123"
         |            }
         |        },
         |        "jurisdiction" : "England_and_Wales"
         |    },
         |    "accountingDetails" : {
         |        "accountingDateStatus" : "FUTURE_DATE",
         |        "startDateOfBusiness" : "9999-12-12"
         |    },
         |    "tradingDetails" : {
         |        "regularPayments" : true
         |    },
         |    "contactDetails" : {
         |        "contactFirstName" : "testName",
         |        "contactDaytimeTelephoneNumber" : "0123456789",
         |        "contactMobileNumber" : "0123456789",
         |        "contactEmail" : "foo@bar.wibble"
         |    },
         |    "accountsPreparation" : {
         |        "businessEndDateChoice" : "COMPANY_DEFINED",
         |        "businessEndDate" : "9999-12-21"
         |    }
         |}""".stripMargin)
  }
}
