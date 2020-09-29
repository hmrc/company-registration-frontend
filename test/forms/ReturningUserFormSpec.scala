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

import fixtures.PPOBFixture
import uk.gov.hmrc.play.test.UnitSpec

class ReturningUserFormSpec extends UnitSpec with PPOBFixture {

  val form = ReturningUserForm.form

  "create a new form with empty string" should {
    val data: Map[String, String] = Map(
      "returningUser" -> "")
    lazy val boundForm = form.bind(data)
    "have errors" in {
      boundForm.errors.map(_.key) shouldBe List("returningUser")
      boundForm.errors.map(_.message) shouldBe List("error.returningUser.required")
    }
  }
}