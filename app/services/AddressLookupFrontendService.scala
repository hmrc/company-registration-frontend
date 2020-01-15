/*
 * Copyright 2020 HM Revenue & Customs
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

import config.FrontendAppConfig
import javax.inject.Inject
import connectors.AddressLookupConnector
import models.NewAddress
import play.api.i18n.MessagesApi
import play.api.libs.json._
import play.api.mvc.{Call, Request}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

case class QueryStringMissingException() extends NoStackTrace

class AddressLookupFrontendServiceImpl @Inject()(val addressLookupFrontendConnector: AddressLookupConnector,
                                                 val metricsService: MetricsService,
                                                 val frontendAppConfig: FrontendAppConfig,
                                                 val groupService: GroupService,
                                                 val messagesApi: MessagesApi) extends AddressLookupFrontendService{
  lazy val addressLookupFrontendURL: String = frontendAppConfig.baseUrl("address-lookup-frontend")
  lazy val companyRegistrationFrontendURL: String = frontendAppConfig.self

  lazy val timeoutInSeconds = frontendAppConfig.timeoutInSeconds.toInt
}

case class JourneyConfig(
                        topLevelConfig:JsObject,
                        timeoutConfig:JsObject,
                        lookupPageConfig: JsObject,
                        selectPageConfig: JsObject,
                        editPageConfig: JsObject,
                        confirmPageConfig: JsObject
                        ) {
  def deepMerge:JsObject = {
    topLevelConfig
      .deepMerge(timeoutConfig)
      .deepMerge(lookupPageConfig)
      .deepMerge(selectPageConfig)
      .deepMerge(editPageConfig)
      .deepMerge(confirmPageConfig)
  }
}


trait AddressLookupFrontendService {

  val addressLookupFrontendConnector: AddressLookupConnector
  val metricsService: MetricsService
  val groupService: GroupService
  val addressLookupFrontendURL : String
  val companyRegistrationFrontendURL : String
  val timeoutInSeconds : Int
  val messagesApi: MessagesApi
  private[services] def messageKey(specificJourneyKey: String, mAPI: MessagesApi = messagesApi)(key: String): String =  {
    val potentialKey = s"page.addressLookup.$specificJourneyKey.$key"
    val probableKey = s"page.addressLookup.$key"
     if (mAPI.isDefinedAt(probableKey)) {
       probableKey
     } else {
       potentialKey
     }
  }

private def topLevelConfigGenerator(continueUrl:String):JsObject = {
  Json.obj(
    "continueUrl" -> s"$continueUrl",
    "homeNavHref" -> "http://www.hmrc.gov.uk/",
    "navTitle" -> messagesApi("common.service.name"),
    "showPhaseBanner" -> true,
    "alphaPhase" -> false,
    "phaseBannerHtml" -> "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
    "includeHMRCBranding" -> false,
    "showBackButtons" -> true,
    "deskProServiceName" -> "SCRS")
}
 private def continueUrl(call:Call): String = {
   s"$companyRegistrationFrontendURL${call.url}"
 }
   private[services] def initForPage(signOutUrl: String, call: Call, specificJourneyKey: String): JsObject = initConfig(signOutUrl,call,specificJourneyKey).deepMerge

   private[services] def initForGroups(regId:String, signOutUrl: String, call: Call)(implicit hc:HeaderCarrier): Future[JsObject] = {
     val initialJourneyConfig = initConfig(signOutUrl,call,"Groups")

     groupService.retrieveGroups(regId).map{
       optGroups =>
         val groups = optGroups.getOrElse(throw new Exception("[initForGroups] user attempted to skip to ALF without walking the groups flow"))
         val groupName = groups.nameOfCompany.getOrElse(throw new Exception("[initForGroups] user attempted to skip to ALF without saving a group name"))
         def partialFunc: PartialFunction[(String, JsValue), (String, JsValue)] = {
           case (key, value) if key == "heading" => (key, JsString(value.as[String].replace("%%",groupName.name)))
           case (key, value) => (key,value)
         }
         val lookupJsObject = Json.obj("lookupPage" -> JsObject((initialJourneyConfig.lookupPageConfig \ "lookupPage").as[JsObject].fields.collect{partialFunc}))
         val confirmJsObject = Json.obj("confirmPage" -> JsObject((initialJourneyConfig.confirmPageConfig \ "confirmPage").as[JsObject].fields.collect{partialFunc}))
         initialJourneyConfig.copy(
           lookupPageConfig = lookupJsObject,
             confirmPageConfig = confirmJsObject
         ).deepMerge
     }
   }


  private[services] def initConfig(signOutUrl: String, call: Call, specificJourneyKey: String = "PPOB"): JourneyConfig = {
    val messageKeyWithSpecKey = messageKey(specificJourneyKey)(_)
    JourneyConfig(
      topLevelConfig = topLevelConfigGenerator(continueUrl(call)),
      timeoutConfig = Json.obj( "timeout" -> Json.obj(
        "timeoutAmount" -> timeoutInSeconds,
        "timeoutUrl" -> s"$companyRegistrationFrontendURL$signOutUrl"
      )),
      lookupPageConfig = Json.obj("lookupPage" -> Json.obj(
        "title" -> messagesApi(messageKeyWithSpecKey("lookup.title")),
        "heading" -> messagesApi(messageKeyWithSpecKey("lookup.heading")),
        "filterLabel" -> messagesApi(messageKeyWithSpecKey("lookup.filter")),
        "submitLabel" -> messagesApi(messageKeyWithSpecKey("lookup.submit")),
        "manualAddressLinkText" -> messagesApi(messageKeyWithSpecKey("lookup.manual"))
      )),
      selectPageConfig = Json.obj(
      "selectPage" -> Json.obj(
        "title" -> messagesApi(messageKeyWithSpecKey("select.description")),
        "heading" -> messagesApi(messageKeyWithSpecKey("select.description")),
        "proposalListLimit" -> 30,
        "showSearchAgainLink" -> true,
        "searchAgainLinkText" -> messagesApi(messageKeyWithSpecKey("select.searchAgain")),
        "editAddressLinkText" -> messagesApi(messageKeyWithSpecKey("select.editAddress"))
      )),
      editPageConfig = Json.obj(
      "editPage" -> Json.obj(
        "title" -> messagesApi(messageKeyWithSpecKey("edit.description")),
        "heading" -> messagesApi(messageKeyWithSpecKey("edit.description")),
        "line1Label" -> messagesApi(messageKeyWithSpecKey("edit.line1")),
        "line2Label" -> messagesApi(messageKeyWithSpecKey("edit.line2")),
        "line3Label" -> messagesApi(messageKeyWithSpecKey("edit.line3")),
        "showSearchAgainLink" -> true
      )),
      confirmPageConfig = Json.obj(
      "confirmPage" -> Json.obj(
        "title" -> messagesApi(messageKeyWithSpecKey("confirm.title")),
        "heading" -> messagesApi(messageKeyWithSpecKey("confirm.description")),
        "showSubHeadingAndInfo" -> false,
        "submitLabel" -> messagesApi(messageKeyWithSpecKey("confirm.continue")),
        "showSearchAgainLink" -> false,
        "showChangeLink" -> true,
        "changeLinkText" -> messagesApi(messageKeyWithSpecKey("confirm.change"))
      ))
    )
  }

    def buildAddressLookupUrl(call: Call, specificJourneyKey: String)(implicit hc: HeaderCarrier): Future[String] = {
    addressLookupFrontendConnector.getOnRampURL(
      initForPage(
        controllers.reg.routes.SignInOutController.timeoutShow().url,
        call,
      specificJourneyKey)
    )
  }
  def buildAddressLookupUrlGroups(regId:String, call:Call, specificJourneyKey: String)(implicit hc:HeaderCarrier): Future[String] = {
    initForGroups(
      regId,
      controllers.reg.routes.SignInOutController.timeoutShow().url,
      call).flatMap {
      jsObject => addressLookupFrontendConnector.getOnRampURL(jsObject)
    }
  }

  def getAddress(implicit hc: HeaderCarrier, request: Request[_]): Future[NewAddress] = {
    request.getQueryString("id") match {
      case Some(id) => addressLookupFrontendConnector.getAddress(id)
      case None => throw new QueryStringMissingException
    }
  }
}
