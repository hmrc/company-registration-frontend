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

package connectors

import config.{FrontendAppConfig, FrontendConfig, WSHttp}
import models.NewAddress
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.util.control.NoStackTrace
import uk.gov.hmrc.http._
import utils.MessagesSupport

object AddressLookupConnector extends AddressLookupConnector with ServicesConfig {
  val addressLookupFrontendURL: String = baseUrl("address-lookup-frontend")
  val companyRegistrationFrontendURL: String = FrontendConfig.self
  val http = WSHttp
  val timeoutInSeconds = FrontendAppConfig.timeoutInSeconds.toInt
}

class ALFLocationHeaderNotSet extends NoStackTrace

trait AddressLookupConnector extends MessagesSupport {

  val addressLookupFrontendURL : String
  val companyRegistrationFrontendURL : String
  val http : CoreGet with CorePost
  val timeoutInSeconds : Int

  private def messageKey(key: String): String = s"page.addressLookup.$key"

  private def initConfig(signOutUrl: String, call: Call): JsObject = {
    val continueUrl = s"$companyRegistrationFrontendURL${call.url}"
    Json.obj(
      "continueUrl" -> s"$continueUrl",
      "homeNavHref" -> "http://www.hmrc.gov.uk/",
      "navTitle" -> msg.messages("common.service.name"),
      "showPhaseBanner" -> true,
      "alphaPhase" -> false,
      "phaseBannerHtml" -> "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
      "includeHMRCBranding" -> false,
      "showBackButtons" -> true,
      "deskProServiceName" -> "SCRS",
      "lookupPage" -> Json.obj(
        "title" -> msg.messages(messageKey("lookup.title")),
        "heading" -> msg.messages(messageKey("lookup.heading")),
        "filterLabel" -> msg.messages(messageKey("lookup.filter")),
        "submitLabel" -> msg.messages(messageKey("lookup.submit")),
        "manualAddressLinkText" -> msg.messages(messageKey("lookup.manual"))
      ),
      "selectPage" -> Json.obj(
        "title" -> msg.messages(messageKey("select.description")),
        "heading" -> msg.messages(messageKey("select.description")),
        "proposalListLimit" -> 30,
        "showSearchAgainLink" -> true,
        "searchAgainLinkText" -> msg.messages(messageKey("select.searchAgain")),
        "editAddressLinkText" -> msg.messages(messageKey("select.editAddress"))
      ),
      "editPage" -> Json.obj(
        "title" -> msg.messages(messageKey("edit.description")),
        "heading" -> msg.messages(messageKey("edit.description")),
        "line1Label" -> msg.messages(messageKey("edit.line1")),
        "line2Label" -> msg.messages(messageKey("edit.line2")),
        "line3Label" -> msg.messages(messageKey("edit.line3")),
        "showSearchAgainLink" -> true
      ),
      "confirmPage" -> Json.obj(
        "title" -> msg.messages(messageKey("confirm.description")),
        "heading" -> msg.messages(messageKey("confirm.description")),
        "showSubHeadingAndInfo" -> false,
        "submitLabel" -> msg.messages(messageKey("confirm.continue")),
        "showSearchAgainLink" -> false,
        "showChangeLink" -> true,
        "changeLinkText" -> msg.messages(messageKey("confirm.change"))
      ),
      "timeout" -> Json.obj(
        "timeoutAmount" -> timeoutInSeconds,
        "timeoutUrl" -> s"$companyRegistrationFrontendURL$signOutUrl"
      )
    )
  }

  def getOnRampURL(signOutUrl: String, call: Call)(implicit hc: HeaderCarrier): Future[String] = {
    val onRampUrl = s"$addressLookupFrontendURL/api/init"
    val json = initConfig(signOutUrl, call)

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
