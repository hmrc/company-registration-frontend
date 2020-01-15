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

package models

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class CompanyContactDetailsAPiSpec extends UnitSpec {

  "toApiModel Checking simple conversion from CompanyContact details model to api model" should {
    "return an api model" in {
      val expected: CompanyContactDetailsApi =
        CompanyContactDetailsApi(Some("foo"),Some("bar"),Some("wizz"))
     CompanyContactDetails.toApiModel(CompanyContactDetails(Some("foo"),Some("bar"), Some("wizz"),Links(Some("sausages"),None))) shouldBe expected
    }
  }

  "CompanyContactDetailsMongo" should {

    "write into the correct json format when using the prePopWrites" in {
      val companyDetails = CompanyContactDetailsApi(
        Some("email"),
        Some("telNo"),
        Some("mobNo")
      )

      val expectedJson = Json.parse(
        """
          |{
          |  "telephoneNumber":"telNo",
          |  "mobileNumber":"mobNo",
          |  "email":"email"
          |}
        """.stripMargin)

      val companyDetailsPrepPopJson = Json.toJson(companyDetails)(CompanyContactDetailsApi.prePopWrites)
      companyDetailsPrepPopJson shouldBe expectedJson
    }
  }
}