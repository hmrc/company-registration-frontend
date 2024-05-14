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

import javax.inject.Inject
import com.codahale.metrics.{Counter, Timer}
import com.codahale.metrics.SharedMetricRegistries

import scala.concurrent.{ExecutionContext, Future}

class MetricsServiceImpl @Inject()(implicit val ec: ExecutionContext) extends MetricsService {

  private val metricRegistry = SharedMetricRegistries.getOrCreate("deskpro-ticket-queue")

  override lazy val keystoreReadTimer = metricRegistry.timer("keystore-read-timer")
  override lazy val keystoreWriteTimer = metricRegistry.timer("keystore-write-timer")

  override lazy val keystoreReadFailed = metricRegistry.counter("keystore-read-failed-counter")
  override lazy val keystoreWriteFailed = metricRegistry.counter("keystore-write-failed-counter")

  override lazy val keystoreHitCounter = metricRegistry.counter("keystore-hit-counter")
  override lazy val keystoreMissCounter = metricRegistry.counter("keystore-miss-counter")

  override lazy val identityVerificationTimer = metricRegistry.timer("identity-verification-timer")
  override lazy val identityVerificationFailedCounter = metricRegistry.counter("identity-verification-failed-counter")

  override lazy val citizenDetailsTimer = metricRegistry.timer("citizen-details-timer")
  override lazy val numberOfQuestionnairesSubmitted = metricRegistry.counter("number-of-questionnaires-submitted")

  override lazy val blockedByEnrollment = metricRegistry.counter("blocked-by-enrollment")

  override lazy val saveContactDetailsToCRTimer = metricRegistry.timer("save-contact-details-to-cr-timer")
  override lazy val saveAccountingDatesToCRTimer = metricRegistry.timer("save-accounting-dates-to-cr-timer")
  override lazy val saveCompletionCapacityToCRTimer = metricRegistry.timer("save-completion-capacity-to-cr-timer")
  override lazy val savePrepareAccountsToCRTimer = metricRegistry.timer("save-prepare-accounts-to-cr-timer")

  override lazy val saveReviewAddressToCRTimer = metricRegistry.timer("save-review-address-to-cr-timer")
  override lazy val saveTradingDetailsToCRTimer = metricRegistry.timer("save-trading-details-to-cr-timer")
  override lazy val saveFootprintToCRTimer = metricRegistry.timer("save-footprint-to-cr-timer")

  override lazy val retrieveCompanyProfileIITimer = metricRegistry.timer("retrieve-company-profile-from-ii-timer")

  override lazy val deskproResponseTimer = metricRegistry.timer("deskpro-call-timer")
}

trait MetricsService {

  val keystoreReadTimer: Timer
  val keystoreWriteTimer: Timer
  val keystoreReadFailed: Counter
  val keystoreWriteFailed: Counter
  val keystoreHitCounter: Counter
  val keystoreMissCounter: Counter
  val identityVerificationTimer: Timer
  val identityVerificationFailedCounter: Counter
  val citizenDetailsTimer: Timer
  val numberOfQuestionnairesSubmitted : Counter
  val deskproResponseTimer: Timer

  val blockedByEnrollment : Counter

  val saveContactDetailsToCRTimer: Timer
  val saveAccountingDatesToCRTimer: Timer
  val saveCompletionCapacityToCRTimer: Timer
  val savePrepareAccountsToCRTimer: Timer
  val saveReviewAddressToCRTimer: Timer
  val saveTradingDetailsToCRTimer: Timer
  val saveFootprintToCRTimer: Timer

  val retrieveCompanyProfileIITimer: Timer

  def processDataResponseWithMetrics[T](timer: Timer.Context, success: Option[Counter] = None, failed: Option[Counter] = None)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f map { data =>
      timer.stop()
      success foreach (_.inc(1))
      data
    } recover {
      case e =>
        timer.stop()
        failed foreach (_.inc(1))
        throw e
    }
  }
}
