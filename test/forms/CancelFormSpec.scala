/*
 * Copyright 2018 HM Revenue & Customs
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

import uk.gov.hmrc.play.test.UnitSpec

class CancelFormSpec extends UnitSpec{

  val form = CancelForm.form

  "valid form" should {
    "return no errors" in {
      val data = Map("cancelService" -> "true")
      form.bind(data).hasErrors shouldBe(false)
    }
  }
  "invalid form" should {
    "return errors" in {
      val data = Map("cancelService" -> "foo")
    form.bind(data).hasErrors shouldBe(true)
    }
  }
}
