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

import fixtures.PPOBFixture
import uk.gov.hmrc.play.test.UnitSpec


class ALFFormSpec extends UnitSpec with PPOBFixture {

  val form = PPOBForm.aLFForm

  "Creating a form with a valid post" when {
    "Enter a valid choice" should {
      val data: Map[String, String] = Map(
        "addressChoice" -> "RO")

      lazy val boundForm = form.bind(data)

      "Have no errors" in {
        boundForm.hasErrors shouldBe false
      }
    }
  }

  "Creating a form with a invalid post" when {
    "Enter an invalid choice (empty string)" should {
      val data: Map[String, String] = Map(
        "addressChoice" -> "")

      lazy val boundForm = form.bind(data)

      "Have no errors" in {
        boundForm.hasErrors shouldBe true
      }
    }
  }
}