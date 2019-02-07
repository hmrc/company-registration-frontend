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

import javax.inject.Inject

import connectors.AddressLookupConnector
import models.NewAddress
import play.api.mvc.{Call, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.util.control.NoStackTrace

case class QueryStringMissingException() extends NoStackTrace

class AddressLookupFrontendServiceImpl @Inject()(val addressLookupFrontendConnector: AddressLookupConnector,
                                                 val metricsService: MetricsService) extends AddressLookupFrontendService
trait AddressLookupFrontendService {

  val addressLookupFrontendConnector: AddressLookupConnector
  val metricsService: MetricsService

  def buildAddressLookupUrl(signOutUrl: String, call: Call)(implicit hc: HeaderCarrier): Future[String] = {
    addressLookupFrontendConnector.getOnRampURL(
      controllers.reg.routes.SignInOutController.timeoutShow().url,
      call
    )
  }

  def getAddress(implicit hc: HeaderCarrier, request: Request[_]): Future[NewAddress] = {
    request.getQueryString("id") match {
      case Some(id) => addressLookupFrontendConnector.getAddress(id)
      case None => throw new QueryStringMissingException
    }
  }
}
