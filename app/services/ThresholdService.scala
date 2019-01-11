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

package services

import connectors.VatThresholdConnector
import org.joda.time.LocalDate
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future

object ThresholdService extends ThresholdService with ServicesConfig {
  val vatThresholdConnector = VatThresholdConnector
  override def now: LocalDate = LocalDate.now()
}

trait ThresholdService {

  def now: LocalDate

  def fetchCurrentVatThreshold(implicit hc: HeaderCarrier): Future[String] = VatThresholdConnector.getVATThreshold(now)
}