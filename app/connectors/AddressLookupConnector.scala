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

package connectors

import javax.inject.Inject

import config.{FrontendAppConfig, WSHttp}
import models.NewAddress
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.http._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

class AddressLookupConnectorImpl @Inject()(val wSHttp: WSHttp,
                                           frontendAppConfig: FrontendAppConfig,
                                           val messagesApi: MessagesApi) extends AddressLookupConnector {
  lazy val addressLookupFrontendURL: String = frontendAppConfig.baseUrl("address-lookup-frontend")
  lazy val companyRegistrationFrontendURL: String = frontendAppConfig.self

  lazy val timeoutInSeconds = frontendAppConfig.timeoutInSeconds.toInt
}

class ALFLocationHeaderNotSet extends NoStackTrace

trait AddressLookupConnector extends I18nSupport {

  val addressLookupFrontendURL : String
  val companyRegistrationFrontendURL : String
  val wSHttp : CoreGet with CorePost
  val timeoutInSeconds : Int

  private def messageKey(key: String): String = s"page.addressLookup.$key"

  private def initConfig(signOutUrl: String, call: Call): JsObject = {
    val continueUrl = s"$companyRegistrationFrontendURL${call.url}"
    Json.obj(
      "continueUrl" -> s"$continueUrl",
      "homeNavHref" -> "http://www.hmrc.gov.uk/",
      "navTitle" -> messagesApi("common.service.name"),
      "showPhaseBanner" -> true,
      "alphaPhase" -> false,
      "phaseBannerHtml" -> "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
      "includeHMRCBranding" -> false,
      "showBackButtons" -> true,
      "deskProServiceName" -> "SCRS",
      "lookupPage" -> Json.obj(
        "title" -> messagesApi(messageKey("lookup.title")),
        "heading" -> messagesApi(messageKey("lookup.heading")),
        "filterLabel" -> messagesApi(messageKey("lookup.filter")),
        "submitLabel" -> messagesApi(messageKey("lookup.submit")),
        "manualAddressLinkText" -> messagesApi(messageKey("lookup.manual"))
      ),
      "selectPage" -> Json.obj(
        "title" -> messagesApi(messageKey("select.description")),
        "heading" -> messagesApi(messageKey("select.description")),
        "proposalListLimit" -> 30,
        "showSearchAgainLink" -> true,
        "searchAgainLinkText" -> messagesApi(messageKey("select.searchAgain")),
        "editAddressLinkText" -> messagesApi(messageKey("select.editAddress"))
      ),
      "editPage" -> Json.obj(
        "title" -> messagesApi(messageKey("edit.description")),
        "heading" -> messagesApi(messageKey("edit.description")),
        "line1Label" -> messagesApi(messageKey("edit.line1")),
        "line2Label" -> messagesApi(messageKey("edit.line2")),
        "line3Label" -> messagesApi(messageKey("edit.line3")),
        "showSearchAgainLink" -> true
      ),
      "confirmPage" -> Json.obj(
        "title" -> messagesApi(messageKey("confirm.description")),
        "heading" -> messagesApi(messageKey("confirm.description")),
        "showSubHeadingAndInfo" -> false,
        "submitLabel" -> messagesApi(messageKey("confirm.continue")),
        "showSearchAgainLink" -> false,
        "showChangeLink" -> true,
        "changeLinkText" -> messagesApi(messageKey("confirm.change"))
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

    wSHttp.POST[JsObject, HttpResponse](onRampUrl, json) map {
      response =>
        response.header("Location").getOrElse {
          Logger.error("[AddressLookupConnector] [getOnRampURL] Location header not set in Address Lookup response")
          throw new ALFLocationHeaderNotSet
        }
    }
  }

  def getAddress(id: String)(implicit hc: HeaderCarrier): Future[NewAddress] = {
    implicit val rds = NewAddress.addressLookupReads
    wSHttp.GET[NewAddress](s"$addressLookupFrontendURL/api/confirmed?id=$id")
  }
}