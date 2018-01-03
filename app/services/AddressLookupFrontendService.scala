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

package services

import connectors.AddressLookupConnector
import models.NewAddress
import play.api.mvc.{Call, Request}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http.HeaderCarrier

case class QueryStringMissingException() extends NoStackTrace

object AddressLookupFrontendService extends AddressLookupFrontendService with ServicesConfig {
  override val addressLookupFrontendConnector = AddressLookupConnector
  override val metricsService = MetricsService
}

trait AddressLookupFrontendService {

  val addressLookupFrontendConnector: AddressLookupConnector
  val metricsService: MetricsService

  def buildAddressLookupUrl(query: String, call: Call)(implicit hc: HeaderCarrier): Future[String] = {
    addressLookupFrontendConnector.getOnRampURL(query, call)
  }

  def getAddress(implicit hc: HeaderCarrier, request: Request[_]): Future[NewAddress] = {
    request.getQueryString("id") match {
      case Some(id) => addressLookupFrontendConnector.getAddress(id)
      case None => throw new QueryStringMissingException
    }
  }
}
