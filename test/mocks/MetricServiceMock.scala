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

package mocks

import com.codahale.metrics.{Counter, Timer}
import org.scalatestplus.mockito.MockitoSugar
import services._

object MetricServiceMock extends MetricsService with MockitoSugar {

  lazy val mockContext = mock[Timer.Context]
  val mockTimer = new Timer()
  val mockCounter = mock[Counter]

  override val keystoreReadTimer: Timer = mockTimer
  override val keystoreWriteTimer: Timer = mockTimer
  override val keystoreReadFailed: Counter = mockCounter
  override val keystoreWriteFailed: Counter = mockCounter
  override val keystoreHitCounter: Counter = mockCounter
  override val keystoreMissCounter: Counter = mockCounter
  override val identityVerificationTimer: Timer = mockTimer
  override val identityVerificationFailedCounter: Counter = mockCounter
  override val citizenDetailsTimer: Timer = mockTimer
  override val numberOfQuestionnairesSubmitted : Counter = mockCounter
  override val saveContactDetailsToCRTimer: Timer = mockTimer
  override val saveAccountingDatesToCRTimer: Timer = mockTimer
  override val saveCompletionCapacityToCRTimer: Timer = mockTimer
  override val savePrepareAccountsToCRTimer: Timer = mockTimer
  override val saveReviewAddressToCRTimer: Timer = mockTimer
  override val saveTradingDetailsToCRTimer: Timer = mockTimer
  override val saveFootprintToCRTimer: Timer = mockTimer
  override val blockedByEnrollment : Counter = mockCounter
  override val retrieveCompanyProfileIITimer: Timer = mockTimer
  override val deskproResponseTimer: Timer = mockTimer
}
