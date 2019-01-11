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

package models

import uk.gov.hmrc.play.test.UnitSpec

class AccountingDatesModelSpec extends UnitSpec {

  "Checking simple conversion from API model to UI form model" should {
    "explicitly work" in {
      val x: AccountingDatesModel = AccountingDatesModel.toModel(AccountingDetails("1", Some("1-2-3"), Links(None, None)))
      x shouldBe AccountingDatesModel("1", Some("1"), Some("2"), Some("3"))
    }
    "implicitly work" in {
      val x: AccountingDatesModel = AccountingDetails("1", Some("1-2-3"), Links(None, None))
      x shouldBe AccountingDatesModel("1", Some("1"), Some("2"), Some("3"))
    }
    "implicitly work with no start date" in {
      val x: AccountingDatesModel = AccountingDetails("1", None, Links(None, None))
      x shouldBe AccountingDatesModel("1", None, None, None)
    }
  }

  "empty" should {
    "create an empty AccountingDatesModel" in {
      AccountingDatesModel.empty shouldBe AccountingDatesModel("", None, None, None)
    }
  }

  "AccountingDetailsRequest.toRequest" should {

    val day = "10"
    val month = "12"
    val year = "2017"

    "format an AccountingDatesModel into an AccountingDetailsRequest correctly" in {
      val model = AccountingDatesModel("test", Some(year), Some(month), Some(day))
      val expected =  AccountingDetailsRequest("test", Some("2017-12-10"))
      AccountingDetailsRequest.toRequest(model) shouldBe expected
    }

    "format an AccountingDatesModel contains single digit dates into an AccountingDetailsRequest correctly" in {
      val d = "1"
      val m = "2"

      val model = AccountingDatesModel("test", Some(year), Some(m), Some(d))
      val expected =  AccountingDetailsRequest("test", Some("2017-02-01"))
      AccountingDetailsRequest.toRequest(model) shouldBe expected
    }
  }

}
