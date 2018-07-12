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

package services.internal

import connectors.{CohoAPIConnector, CohoApiResponse, IncorpInfoConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CheckIncorporationService extends CheckIncorporationService {
  val cohoApiConnector = CohoAPIConnector
  val incorpInfoConnector = IncorpInfoConnector
}

trait CheckIncorporationService {

  val cohoApiConnector: CohoAPIConnector
  val incorpInfoConnector: IncorpInfoConnector

  def fetchIncorporationStatus(timePoint: Option[String], itemsPerPage: Int)(implicit hc: HeaderCarrier): Future[CohoApiResponse] = {
    cohoApiConnector.fetchIncorporationStatus(timePoint, itemsPerPage)
  }

  def incorporateTransactionId(transId: String, isSuccess: Boolean)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      incorporate <- incorpInfoConnector.injectTestIncorporationUpdate(transId, isSuccess)
      triggerResponse <- incorpInfoConnector.manuallyTriggerIncorporationUpdate
    } yield incorporate && triggerResponse
  }
}
