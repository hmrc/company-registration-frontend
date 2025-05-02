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

package services

import builders.AuthBuilder
import config.AppConfig
import helpers.SCRSSpec
import mocks.ServiceConnectorMock
import models.JavaTimeUtils.DateTimeUtils.currentDate
import models.VatThreshold
import org.mockito.Mockito.{reset, when}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils.SCRSFeatureSwitches

import java.time._


class ThresholdServiceSpec extends SCRSSpec with ServiceConnectorMock with AuthBuilder with GuiceOneAppPerSuite {
  implicit val mockConfig: AppConfig = mock[AppConfig]
  class TestThresholdService(fakeNow: LocalDate) extends ThresholdService()(mockConfig) {
    override def now: LocalDate = fakeNow
  }

  def testService(fakeNow: LocalDate = currentDate) = new TestThresholdService(fakeNow)

  val dateTime: LocalDate = LocalDate.of(2017, 4, 1)
  val amount: Int = 85000
  val testThreshold: VatThreshold = VatThreshold(dateTime, amount)

  val dateTime2: LocalDate = LocalDate.of(2024, 4, 1)
  val amount2: Int = 90000
  val testThreshold2: VatThreshold = VatThreshold(dateTime2, amount2)

  override def afterEach(): Unit = {
    reset(mockConfig)
  }
  
  object TestAppConfig extends AppConfig(mock[ServicesConfig], mock[SCRSFeatureSwitches], mock[Configuration]) {
    override lazy val taxYearStartDate: String = LocalDate.now().toString
    override lazy val currentPayeWeeklyThreshold: Int = 10
    override lazy val currentPayeMonthlyThreshold: Int = 20
    override lazy val currentPayeAnnualThreshold: Int = 30
    override lazy val oldPayeWeeklyThreshold: Int = 5
    override lazy val oldPayeMonthlyThreshold: Int = 10
    override lazy val oldPayeAnnualThreshold: Int = 15
  }

  "fetchCurrentPayeThresholds" must {
    "return the old tax years thresholds if the date is before the tax year start date" in {
      object TestService extends ThresholdService()(TestAppConfig) {
        override def now: LocalDate = LocalDate.now().minusDays(1)
      }

      val result = TestService.fetchCurrentPayeThresholds()
      result mustBe Map("weekly" -> 5, "monthly" -> 10, "annually" -> 15)
    }

    "return the new tax years thresholds if the date is on the tax year start date" in {
      object TestService extends ThresholdService()(TestAppConfig)

      val result = TestService.fetchCurrentPayeThresholds()
      result mustBe Map("weekly" -> 10, "monthly" -> 20, "annually" -> 30)
    }

    "return the new tax years thresholds if the date is after the tax year start date" in {
      object TestService extends ThresholdService()(TestAppConfig) {
        override def now: LocalDate = LocalDate.now().plusDays(1)
      }

      val result = TestService.fetchCurrentPayeThresholds()
      result mustBe Map("weekly" -> 10, "monthly" -> 20, "annually" -> 30)
    }
  }

  "getVatThreshold" when {
    "there is a single threshold in the config" must {
      "return the threshold value" in {
        when(mockConfig.thresholds).thenReturn(Seq(testThreshold))
        val expectedResults = Some(testThreshold)
        val actualResult = testService().getVatThreshold()

        expectedResults mustBe actualResult
      }
    }

    "there are multiple thresholds in the config before today" must {
      "return the newest threshold value" in {
        when(mockConfig.thresholds).thenReturn(Seq(testThreshold, testThreshold2))
        val expectedResults = Some(testThreshold2)
        val actualResult = testService(dateTime2.plusDays(1)).getVatThreshold()

        expectedResults mustBe actualResult
      }
    }

    "there are multiple thresholds in the config but only 1 before today" must {
      "return the threshold in the past" in {
        when(mockConfig.thresholds).thenReturn(Seq(testThreshold, testThreshold2))
        val expectedResults = Some(testThreshold)
        val actualResult = testService(dateTime2.minusDays(1)).getVatThreshold()

        expectedResults mustBe actualResult
      }
    }


    "there are no thresholds in the config before today" must {
      "return none" in {
        when(mockConfig.thresholds).thenReturn(Seq(testThreshold, testThreshold2))
        val expectedResults = None
        val actualResult = testService(dateTime.minusDays(1)).getVatThreshold()

        expectedResults mustBe actualResult
      }
    }

    "there are no thresholds in the config" must {
      "return none" in {
        when(mockConfig.thresholds).thenReturn(Seq())
        val expectedResults = None
        val actualResult = testService().getVatThreshold()

        expectedResults mustBe actualResult
      }
    }
  }
}
