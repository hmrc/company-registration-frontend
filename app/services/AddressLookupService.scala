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

import address.client.{AddressRecord, RecordSet}
import config.{FrontendAppConfig, WSHttp}
import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpReads}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait AddressLookupResponse
case class AddressLookupSuccessResponse(addressList: RecordSet) extends AddressLookupResponse
case class AddressLookupErrorResponse(cause: Exception) extends AddressLookupResponse

class AddressLookupServiceImpl @Inject()(val appConfig: FrontendAppConfig,
                                         val wSHttp: WSHttp) extends AddressLookupService {

  override lazy val addressLookupUrl = appConfig.baseUrl("address-lookup")
}

trait AddressLookupService extends AddressConverter {
  val wSHttp: CoreGet
  val addressLookupUrl: String

  def lookup(postcode: String, optFilter: Option[String] = None)(implicit hc: HeaderCarrier): Future[AddressLookupResponse] = {
    val scrsHc = hc.withExtraHeaders("X-Hmrc-Origin" -> "SCRS")
    val trimmedPostcode = postcode.replaceAll(" ", "")
    val filter = checkFilter(optFilter)
    val addressJson = wSHttp.GET[JsValue](s"$addressLookupUrl/uk/addresses?postcode=$trimmedPostcode$filter")(implicitly[HttpReads[JsValue]], scrsHc, implicitly)
    handleResponse(addressJson)
  }

  def lookup(uprn: String)(implicit hc: HeaderCarrier): Future[AddressRecord] = {
    val scrsHc = hc.withExtraHeaders("X-Hmrc-Origin" -> "SCRS")
    wSHttp.GET[AddressRecord](s"$addressLookupUrl/v2/uk/addresses/$uprn")(implicitly[HttpReads[AddressRecord]], scrsHc, implicitly)
  }

  private[services] def handleResponse(addressJson: Future[JsValue]): Future[AddressLookupResponse] = {
    addressJson.map {
      res => AddressLookupSuccessResponse(RecordSet.fromJsonAddressLookupService(res))
    } recover {
      case e: Exception =>
        Logger.warn("Error received from address lookup service", e)
        AddressLookupErrorResponse(e)
    }
  }

  private[services] def checkFilter(filter: Option[String]): String = {
    filter.fold("")(f => s"&filter=${f.replaceAll(" ", "+")}")
  }
}