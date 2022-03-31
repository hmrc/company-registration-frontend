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

package connectors

import config.{AppConfig, WSHttp}
import javax.inject.Inject
import models.{AlfJourneyConfig, NewAddress}
import play.api.Logging
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class AddressLookupConnectorImpl @Inject()(val wSHttp: WSHttp,
                                           appConfig: AppConfig) extends AddressLookupConnector {
  lazy val addressLookupFrontendURL: String = appConfig.servicesConfig.baseUrl("address-lookup-frontend")
}

class ALFLocationHeaderNotSet extends NoStackTrace

trait AddressLookupConnector extends Logging {

  val addressLookupFrontendURL: String
  val wSHttp: CoreGet with CorePost

  def getOnRampURL(alfJourneyConfig: AlfJourneyConfig)(implicit hc: HeaderCarrier): Future[String] = {
    val onRampUrl = s"$addressLookupFrontendURL/api/v2/init"
    val locationKey = "Location"

    wSHttp.POST[AlfJourneyConfig, HttpResponse](onRampUrl, alfJourneyConfig) map {
      response =>
        response.header(key = locationKey).getOrElse {
          logger.error("[AddressLookupConnector] [getOnRampURL] Location header not set in Address Lookup response")
          throw new ALFLocationHeaderNotSet
        }
    }
  }

  def getAddress(id: String)(implicit hc: HeaderCarrier): Future[NewAddress] = {
    implicit val rds = NewAddress.addressLookupReads
    wSHttp.GET[NewAddress](s"$addressLookupFrontendURL/api/confirmed?id=$id")
  }
}