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

package services

import javax.inject.Inject

import com.codahale.metrics.{Counter, Timer}
import com.kenshoo.play.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetricsServiceImpl @Inject()(metrics:Metrics) extends MetricsService {

  override lazy val keystoreReadTimer = metrics.defaultRegistry.timer("keystore-read-timer")
  override lazy val keystoreWriteTimer = metrics.defaultRegistry.timer("keystore-write-timer")

  override lazy val keystoreReadFailed = metrics.defaultRegistry.counter("keystore-read-failed-counter")
  override lazy val keystoreWriteFailed = metrics.defaultRegistry.counter("keystore-write-failed-counter")

  override lazy val keystoreHitCounter = metrics.defaultRegistry.counter("keystore-hit-counter")
  override lazy val keystoreMissCounter = metrics.defaultRegistry.counter("keystore-miss-counter")

  override lazy val identityVerificationTimer = metrics.defaultRegistry.timer("identity-verification-timer")
  override lazy val identityVerificationFailedCounter = metrics.defaultRegistry.counter("identity-verification-failed-counter")

  override lazy val citizenDetailsTimer = metrics.defaultRegistry.timer("citizen-details-timer")
  override lazy val numberOfQuestionnairesSubmitted = metrics.defaultRegistry.counter("number-of-questionnaires-submitted")

  override lazy val blockedByEnrollment = metrics.defaultRegistry.counter("blocked-by-enrollment")

  override lazy val saveContactDetailsToCRTimer = metrics.defaultRegistry.timer("save-contact-details-to-cr-timer")
  override lazy val saveAccountingDatesToCRTimer = metrics.defaultRegistry.timer("save-accounting-dates-to-cr-timer")
  override lazy val saveCompletionCapacityToCRTimer = metrics.defaultRegistry.timer("save-completion-capacity-to-cr-timer")
  override lazy val savePrepareAccountsToCRTimer = metrics.defaultRegistry.timer("save-prepare-accounts-to-cr-timer")

  override lazy val saveReviewAddressToCRTimer = metrics.defaultRegistry.timer("save-review-address-to-cr-timer")
  override lazy val saveTradingDetailsToCRTimer = metrics.defaultRegistry.timer("save-trading-details-to-cr-timer")
  override lazy val saveFootprintToCRTimer = metrics.defaultRegistry.timer("save-footprint-to-cr-timer")

  override lazy val retrieveCompanyProfileIITimer = metrics.defaultRegistry.timer("retrieve-company-profile-from-ii-timer")

  override lazy val deskproResponseTimer = metrics.defaultRegistry.timer("deskpro-call-timer")
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

  def processDataResponseWithMetrics[T](timer: Timer.Context, success: Option[Counter] = None, failed: Option[Counter] = None)(f: => Future[T]): Future[T] = {
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