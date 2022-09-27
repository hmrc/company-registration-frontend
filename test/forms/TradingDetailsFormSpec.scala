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

import helpers.UnitSpec

class TradingDetailsFormSpec extends UnitSpec {

  class Setup {

    val testForm = TradingDetailsForm.form

    val trueData = Map("regularPayments" -> "true")
    val falseData = Map("regularPayments" -> "false")
    val invalidData = Map("regularPayments" -> "")
  }

  "Creating a form using an empty model" should {
    "return an empty string for amount" in new Setup {
      testForm.data.isEmpty mustBe true
    }
  }

  "Creating a form with a valid post" when {
    "selecting yes" should {
      "have no errors" in new Setup {
        testForm.bind(trueData).hasErrors mustBe false
      }
    }

    "selecting no" should {
      "have no errors" in new Setup {
        testForm.bind(falseData).hasErrors mustBe false
      }
    }
  }

  "Creating a form with an invalid post" when {
    "no regularPayment option is selected" should {
      "have errors" in new Setup {
        testForm.bind(invalidData).errors.map(_.key) mustBe List("regularPayments")
      }
    }
  }
}