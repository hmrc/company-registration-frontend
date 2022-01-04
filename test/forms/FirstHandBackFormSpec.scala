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

package forms

import fixtures.PayloadFixture
import helpers.SCRSSpec
import models.CHROAddress
import models.handoff.CompanyNameHandOffFormModel

class FirstHandBackFormSpec extends SCRSSpec with PayloadFixture {

  "Creating a form with valid data" should {

    val form = FirstHandBackForm.form

    "have no errors" in {

      form.bind(handBackFormData.toMap).get shouldBe CompanyNameHandOffFormModel(
        Some("testID"),
        "testID",
        "testCompanyName",
        CHROAddress(
          "premises",
          "line1",
          Some("line2"),
          "locality",
          "country",
          Some("POBox"),
          Some("POCode"),
          Some("region")
        ),
        "testJurisdiction",
        "ch",
        "hmrc"
      )
    }
  }
}
