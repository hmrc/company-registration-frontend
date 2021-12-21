/*
 * Copyright 2021 HM Revenue & Customs
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

class ConfirmRegistrationEmailFormSpec extends UnitSpec {

  class Setup {

    val testForm = ConfirmRegistrationEmailForm.form

    val trueData = Map("confirmRegistrationEmail" -> "true")
    val falseData = Map("confirmRegistrationEmail" -> "false")
    val invalidData = Map("confirmRegistrationEmail" -> "")
  }

  "Creating a form using an empty model" should {
    "return an empty value" in new Setup {
      testForm.data.isEmpty shouldBe true
    }
  }

  "Creating a form with a valid post" when {
    "selecting yes" should {
      "have no errors" in new Setup {
        testForm.bind(trueData).hasErrors shouldBe false
      }
    }

    "selecting no" should {
      "have no errors" in new Setup {
        testForm.bind(falseData).hasErrors shouldBe false
      }
    }
  }

  "Creating a form with an invalid post" when {
    "no confirm email option is selected" should {
      "have errors" in new Setup {
        testForm.bind(invalidData).errors.map(_.key) shouldBe List("confirmRegistrationEmail")

      }
    }
  }
}
