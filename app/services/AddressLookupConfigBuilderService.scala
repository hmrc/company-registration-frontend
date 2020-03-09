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
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.MessagesApi
import play.api.mvc.Call

@Singleton
class AddressLookupConfigBuilderService @Inject()(appConfig: FrontendAppConfig) {

  lazy val companyRegistrationFrontendURL: String = appConfig.self
  lazy val timeoutLength: Int = appConfig.timeoutInSeconds.toInt

  def buildConfig(handbackLocation: Call, specificJourneyKey: String, lookupPageHeading: String, confirmPageHeading: String)(implicit messagesApi: MessagesApi): AlfJourneyConfig = {

    val messageKeyWithSpecKey: String => String = (key: String) => {
      val journeySpecificAlfMessageKey = s"page.addressLookup.$specificJourneyKey.$key"
      val addressLookupMessageKey = s"page.addressLookup.$key"

      if (messagesApi.isDefinedAt(addressLookupMessageKey)) addressLookupMessageKey else journeySpecificAlfMessageKey
    }

    val topLevelConfig = TopLevelConfig(
      continueUrl = s"$companyRegistrationFrontendURL${handbackLocation.url}",
      homeNavHref = "http://www.hmrc.gov.uk/",
      navTitle = messagesApi("common.service.name"),
      showPhaseBanner = true,
      alphaPhaseBanner = false,
      phaseBannerHtml = "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
      includeHMRCBranding = false,
      showBackButtons = true,
      deskProServiceName = "SCRS"
    )

    val timeoutConfig = TimeoutConfig(
      timeoutAmount = timeoutLength,
      timeoutUrl = s"$companyRegistrationFrontendURL${controllers.reg.routes.SignInOutController.timeoutShow().url}"
    )

    val lookupPageConfig = LookupPageConfig(
      title = messagesApi(messageKeyWithSpecKey("lookup.title")),
      heading = lookupPageHeading,
      filterLabel = messagesApi(messageKeyWithSpecKey("lookup.filter")),
      submitLabel = messagesApi(messageKeyWithSpecKey("lookup.submit")),
      manualAddressLinkText = messagesApi(messageKeyWithSpecKey("lookup.manual"))
    )

    val selectPageConfig = SelectPageConfig(
      title = messagesApi(messageKeyWithSpecKey("select.description")),
      heading = messagesApi(messageKeyWithSpecKey("select.description")),
      proposalListLimit = 30,
      showSearchAgainLink = true,
      searchAgainLinkText = messagesApi(messageKeyWithSpecKey("select.searchAgain")),
      editAddressLinkText = messagesApi(messageKeyWithSpecKey("select.editAddress"))
    )

    val editPageConfig = EditPageConfig(
      title = messagesApi(messageKeyWithSpecKey("edit.description")),
      heading = messagesApi(messageKeyWithSpecKey("edit.description")),
      line1Label = messagesApi(messageKeyWithSpecKey("edit.line1")),
      line2Label = messagesApi(messageKeyWithSpecKey("edit.line2")),
      line3Label = messagesApi(messageKeyWithSpecKey("edit.line3")),
      showSearchAgainLink = true
    )

    val confirmPageConfig = ConfirmPageConfig(
      title = messagesApi(messageKeyWithSpecKey("confirm.title")),
      heading = confirmPageHeading,
      showSubHeadingAndInfo = false,
      submitLabel = messagesApi(messageKeyWithSpecKey("confirm.continue")),
      showSearchAgainLink = false,
      showChangeLink = true,
      changeLinkText = messagesApi(messageKeyWithSpecKey("confirm.change"))
    )

    AlfJourneyConfig(
      topLevelConfig = topLevelConfig,
      timeoutConfig = timeoutConfig,
      lookupPageConfig = lookupPageConfig,
      selectPageConfig = selectPageConfig,
      editPageConfig = editPageConfig,
      confirmPageConfig = confirmPageConfig
    )

  }

}
