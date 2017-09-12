/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class CompanyContactModelSpec extends UnitSpec {

  "Checking simple conversion from CompanyContact details model to view model" should {
    "implicitly work with an empty details model" in {
      val x: CompanyContactViewModel =
        CompanyContactDetails(None, None, None, None, None, None, Links(None,None))
      x shouldBe CompanyContactDetails.empty
    }
    "implicitly work with a fully populated details model" in {
      val x: CompanyContactViewModel =
        CompanyContactDetails(Some("Foo"), Some("Wibble"), Some("Bar"), Some("1"), Some("5"), Some("foo@bar.wibble"), Links(None,None))
      x shouldBe CompanyContactViewModel("Foo Wibble Bar", Some("foo@bar.wibble"), Some("1"), Some("5"))
    }
    "implicitly work with a semi-populated details model" in {
      val x: CompanyContactViewModel =
        CompanyContactDetails(Some("Foo"), None, Some("Bar"), Some("1"), None, Some("foo@bar.wibble"), Links(None,None))
      x shouldBe CompanyContactViewModel("Foo Bar", Some("foo@bar.wibble"), Some("1"), None)
    }
    "implicitly work with a minimally populated details model" in {
      val x: CompanyContactViewModel =
        CompanyContactDetails(Some("Foo"), None, None, None, None, None, Links(None,None))
      x shouldBe CompanyContactViewModel("Foo", None, None, None)
    }
  }

  "CompanyContactDetailsMongo" should {

    "write into the correct json format when using the prePopWrites" in {
      val companyDetails = CompanyContactDetailsMongo(
        Some("firstName"),
        Some("middle"),
        Some("lastName"),
        Some("telNo"),
        Some("mobNo"),
        Some("email")
      )

      val expectedJson = Json.parse(
        """
          |{
          |  "firstName":"firstName",
          |  "middleName":"middle",
          |  "surname":"lastName",
          |  "telephoneNumber":"telNo",
          |  "mobileNumber":"mobNo",
          |  "email":"email"
          |}
        """.stripMargin)

      val companyDetailsPrepPopJson = Json.toJson(companyDetails)(CompanyContactDetailsMongo.prePopWrites)

      companyDetailsPrepPopJson shouldBe expectedJson
    }
  }
}
