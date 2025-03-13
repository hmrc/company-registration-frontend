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

import config.AppConfig
import helpers.UnitSpec
import mocks.SCRSMocks
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2

class EnrolmentsServiceSpec extends UnitSpec with GuiceOneAppPerSuite with MockitoSugar with SCRSMocks {
  class Setup {
    object TestService extends EnrolmentsService {
      val http = mock[HttpClientV2]
      override val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
    }
  }

  implicit val hc = HeaderCarrier()

  "checkEnrolments" should {
    "return a true" when {
      "any restricted enrolments are found in the enrolments record" in new Setup {
        val result = TestService.hasBannedRegimes(
          Enrolments(Set(Enrolment("IR-SA-TRUST-ORG", Seq(EnrolmentIdentifier("test-paye-identifier", "test-paye-value")), "testState")))
        )
        result mustBe true
      }
    }

    "return a false" when {
      "no restricted enrolments are found in the enrolments record" in new Setup {
        val result = TestService.hasBannedRegimes(Enrolments(Set()))
        result mustBe false
      }
    }
  }
}
