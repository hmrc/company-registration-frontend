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

    val selectPageConfig = SelectPageConfig(
      proposalListLimit = 30,
      showSearchAgainLink = true
    )

    val confirmPageConfig = ConfirmPageConfig(
      showSubHeadingAndInfo = false,
      showSearchAgainLink = false,
      showChangeLink = true
    )

    val timeoutConfig = TimeoutConfig(
      timeoutAmount = timeoutLength,
      timeoutUrl = s"$companyRegistrationFrontendURL${controllers.reg.routes.SignInOutController.timeoutShow().url}"
    )

    val journeyOptions = JourneyOptions(
      continueUrl = s"$companyRegistrationFrontendURL${handbackLocation.url}",
      homeNavHref = "http://www.hmrc.gov.uk/",
      showPhaseBanner = true,
      alphaPhase = false,
      includeHMRCBranding = false,
      showBackButtons = true,
      deskProServiceName = "SCRS",
      selectPageConfig = selectPageConfig,
      confirmPageConfig = confirmPageConfig,
      timeoutConfig = timeoutConfig
    )

    val appLevelLabels = AppLevelLabels(
      navTitle = messagesApi("common.service.name"),
      phaseBannerHtml = "This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>."
    )


    val lookupPageLabels = LookupPageLabels(
      title = messagesApi("page.addressLookup.lookup.title"),
      heading = lookupPageHeading,
      filterLabel = messagesApi("page.addressLookup.lookup.filter"),
      submitLabel = messagesApi("page.addressLookup.lookup.submit"),
      manualAddressLinkText = messagesApi("page.addressLookup.lookup.manual")
    )

    val selectPageLabels = SelectPageLabels(
      title = messagesApi("page.addressLookup.select.description"),
      heading = messagesApi("page.addressLookup.select.description"),
      searchAgainLinkText = messagesApi("page.addressLookup.select.searchAgain"),
      editAddressLinkText = messagesApi("page.addressLookup.select.editAddress")
    )

    val editPageLabels = EditPageLabels(
      title = messagesApi("page.addressLookup.edit.description"),
      heading = messagesApi("page.addressLookup.edit.description"),
      line1Label = messagesApi("page.addressLookup.edit.line1"),
      line2Label = messagesApi("page.addressLookup.edit.line2"),
      line3Label = messagesApi("page.addressLookup.edit.line3")
    )

    val confirmPageLabels = ConfirmPageLabels(
      title = messagesApi("page.addressLookup.confirm.title"),
      heading = confirmPageHeading,
      submitLabel = messagesApi("page.addressLookup.confirm.continue"),
      changeLinkText = messagesApi("page.addressLookup.confirm.change")
    )

    val journeyLabels = JourneyLabels(
      en = LanguageLabels(
        appLevelLabels,
        selectPageLabels,
        lookupPageLabels,
        editPageLabels,
        confirmPageLabels
      )
    )

    AlfJourneyConfig(
      version = AlfJourneyConfig.defaultConfigVersion,
      options = journeyOptions,
      labels = journeyLabels
    )

  }
}
