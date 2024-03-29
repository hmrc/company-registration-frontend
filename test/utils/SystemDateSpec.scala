/*
 * Copyright 2023 HM Revenue & Customs
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

package utils

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import java.time.LocalDate


class SystemDateSpec extends PlaySpec with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    System.clearProperty("feature.system-date")
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    System.clearProperty("feature.system-date")
    super.afterEach()
  }

  "getSystemDate" should {
    "return a LocalDate of today" when {
      "the feature is null" in {
        val result = SystemDate.getSystemDate
        result mustBe LocalDate.now
      }

      "the feature is ''" in {
        System.setProperty("feature.system-date", "")

        val result = SystemDate.getSystemDate
        result mustBe LocalDate.now
      }
    }

    "return a LocalDate that was previously set" in {
      System.setProperty("feature.system-date", "2018-01-01")

      val result = SystemDate.getSystemDate
      result mustBe LocalDate.parse("2018-01-01")
    }
  }
}
