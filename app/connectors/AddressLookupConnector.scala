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

package connectors

import config.{AppConfig, WSHttp}
import connectors.httpParsers.AddressLookupHttpParsers.{addressHttpReads, onRampHttpReads}
import models.{AlfJourneyConfig, NewAddress}
import uk.gov.hmrc.http._
import utils.Logging

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

class AddressLookupConnectorImpl @Inject()(val wSHttp: WSHttp,
                                           appConfig: AppConfig)(implicit val ec: ExecutionContext) extends AddressLookupConnector {
  lazy val addressLookupFrontendURL: String = appConfig.servicesConfig.baseUrl("address-lookup-frontend")
}

class ALFLocationHeaderNotSet extends NoStackTrace

trait AddressLookupConnector extends Logging {

  implicit val ec: ExecutionContext
  val addressLookupFrontendURL: String
  val wSHttp: CoreGet with CorePost

  def getOnRampURL(alfJourneyConfig: AlfJourneyConfig)(implicit hc: HeaderCarrier): Future[String] = {
    wSHttp.POST[AlfJourneyConfig, String](s"$addressLookupFrontendURL/api/v2/init", alfJourneyConfig)(AlfJourneyConfig.journeyConfigFormat, onRampHttpReads, hc, ec)
  }

  def getAddress(id: String)(implicit hc: HeaderCarrier): Future[NewAddress] = {
    wSHttp.GET[NewAddress](s"$addressLookupFrontendURL/api/confirmed?id=$id")(addressHttpReads, hc, ec)
  }
}

class ALFLocationHeaderNotSetException extends NoStackTrace