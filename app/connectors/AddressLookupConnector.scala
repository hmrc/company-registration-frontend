/*
 * Copyright 2017 HM Revenue & Customs
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

import config.{FrontendConfig, WSHttp}
import models.NewAddress
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.util.control.NoStackTrace
import uk.gov.hmrc.http._

object AddressLookupConnector extends AddressLookupConnector with ServicesConfig {
  val addressLookupFrontendURL: String = baseUrl("address-lookup-frontend")
  val companyRegistrationFrontendURL: String = FrontendConfig.self
  val http = WSHttp
}

class ALFLocationHeaderNotSet extends NoStackTrace

trait AddressLookupConnector {

  val addressLookupFrontendURL : String
  val companyRegistrationFrontendURL : String
  val http : CoreGet with CorePost

  def getOnRampURL(query: String, call: Call)(implicit hc: HeaderCarrier): Future[String] = {
    val onRampUrl = s"$addressLookupFrontendURL/api/init/$query"
    val continueUrl = s"$companyRegistrationFrontendURL${call.url}"
    val json = Json.obj("continueUrl" -> s"$continueUrl")

    http.POST[JsObject, HttpResponse](onRampUrl, json) map {
      response =>
        response.header("Location").getOrElse {
          Logger.error("[AddressLookupConnector] [getOnRampURL] Location header not set in Address Lookup response")
          throw new ALFLocationHeaderNotSet
        }
    }
  }

  def getAddress(id: String)(implicit hc: HeaderCarrier): Future[NewAddress] = {
    implicit val rds = NewAddress.addressLookupReads
    http.GET[NewAddress](s"$addressLookupFrontendURL/api/confirmed?id=$id")
  }

}
