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

package forms

import models.AboutYouChoiceForm
import play.api.data.Form
import uk.gov.hmrc.play.test.UnitSpec

class AboutYouFormSpec extends UnitSpec {

  def fillForm(cc : String, other : String) : Form[AboutYouChoiceForm] = {
    AboutYouForm.form.fill(AboutYouChoiceForm(cc, other))
  }

  "Populate form" should {
    "have director and empty" in {
      val result = AboutYouForm.populateForm("director")

      result.get.completionCapacity shouldBe "director"
      result.get.completionCapacityOther shouldBe ""
    }

    "have agent and empty" in {
      val result = AboutYouForm.populateForm("agent")

      result.get.completionCapacity shouldBe "agent"
      result.get.completionCapacityOther shouldBe ""
    }

    "have secretary and empty" in {
      val result = AboutYouForm.populateForm("company secretary")

      result.get.completionCapacity shouldBe "company secretary"
      result.get.completionCapacityOther shouldBe ""
    }

    "have empty and empty" in {
      val result = AboutYouForm.populateForm("")

      result.get.completionCapacity shouldBe ""
      result.get.completionCapacityOther shouldBe ""
    }

    "have other and Other capacity" in {
      val result = AboutYouForm.populateForm("Other capacity")

      result.get.completionCapacity shouldBe "other"
      result.get.completionCapacityOther shouldBe "Other capacity"
    }
  }
}
