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

package services

import config.AppConfig
import javax.inject.{Inject, Singleton}
import models._
import play.api.i18n.{Lang, MessagesApi, MessagesProvider}
import play.api.mvc.Call
import utils.MessageOption

@Singleton
class AddressLookupConfigBuilderService @Inject()(appConfig: AppConfig) {

  val english = Lang("en")
  val welsh = Lang("cy")

  lazy val companyRegistrationFrontendURL: String = appConfig.self
  lazy val timeoutLength: Int = appConfig.timeoutInSeconds.toInt
  lazy val accessibilityFooterUrl: String = appConfig.accessibilityStatementUrl

  def buildConfig(handbackLocation: Call, specificJourneyKey: String, lookupPageHeading: String, confirmPageHeading: String)(implicit messagesApi: MessagesApi, messagesProvider: MessagesProvider): AlfJourneyConfig = {

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
      timeoutUrl = s"$companyRegistrationFrontendURL${controllers.reg.routes.SignInOutController.timeoutShow.url}"
    )

    val manualAddressEntryConfig = ManualAddressEntryConfig(
      line1MaxLength = 255,
      line2MaxLength = 255,
      line3MaxLength = 255,
      townMaxLength = 255,
      mandatoryFields = MandatoryFields(
        addressLine1 = true,
        addressLine2 = true,
        addressLine3 = false,
        town         = false,
        postcode     = true
      )
    )


    val journeyOptions = JourneyOptions(
      continueUrl = s"$companyRegistrationFrontendURL${handbackLocation.url}",
      homeNavHref = "http://www.hmrc.gov.uk/",
      accessibilityFooterUrl = accessibilityFooterUrl,
      showPhaseBanner = true,
      alphaPhase = false,
      includeHMRCBranding = false,
      showBackButtons = true,
      deskProServiceName = "SCRS",
      selectPageConfig = selectPageConfig,
      confirmPageConfig = confirmPageConfig,
      timeoutConfig = timeoutConfig,
      disableTranslations = !appConfig.languageTranslationEnabled,
      manualAddressEntryConfig = manualAddressEntryConfig
    )

    def appLevelLabels(lang: Lang) = {
      AppLevelLabels(
        navTitle = MessageOption("common.service.name", lang)(messagesApi),
        phaseBannerHtml = MessageOption("This is a new service. " +
          "Help us improve it - send your" +
          " <a href='https://www.tax.service.gov.uk/contact/beta-feedback?service=SCRS'>feedback</a>.", lang
      )(messagesApi))
    }


    def lookupPageLabels(lang: Lang) = {
      LookupPageLabels(
        title = MessageOption(lookupPageHeading, lang)(messagesApi),
        heading = MessageOption(lookupPageHeading, lang)(messagesApi),
        filterLabel = MessageOption("page.addressLookup.lookup.filter", lang)(messagesApi),
        submitLabel = MessageOption("page.addressLookup.lookup.submit", lang)(messagesApi),
        manualAddressLinkText = MessageOption("page.addressLookup.lookup.manual", lang)(messagesApi)
      )
    }
    def selectPageLabels(lang: Lang) = {
      SelectPageLabels(
        title = MessageOption("page.addressLookup.select.description", lang)(messagesApi),
        heading = MessageOption("page.addressLookup.select.description", lang)(messagesApi),
        searchAgainLinkText = MessageOption("page.addressLookup.select.searchAgain", lang)(messagesApi),
        editAddressLinkText = MessageOption("page.addressLookup.select.editAddress", lang)(messagesApi)
      )
    }
    def editPageLabels(lang: Lang) = {
      EditPageLabels(
        title = MessageOption("page.addressLookup.edit.description", lang)(messagesApi),
        heading = MessageOption("page.addressLookup.edit.description", lang)(messagesApi),
        line1Label = MessageOption("page.addressLookup.edit.line1", lang)(messagesApi),
        line2Label = MessageOption("page.addressLookup.edit.line2", lang)(messagesApi),
        line3Label = MessageOption("page.addressLookup.edit.line3", lang)(messagesApi)
      )
    }
    def confirmPageLabels(lang: Lang) = {
      ConfirmPageLabels(
        title = MessageOption(confirmPageHeading, lang)(messagesApi),
        heading = MessageOption(confirmPageHeading, lang)(messagesApi),
        submitLabel = MessageOption("page.addressLookup.confirm.continue", lang)(messagesApi),
        changeLinkText = MessageOption("page.addressLookup.confirm.change", lang)(messagesApi)
      )
    }
    val journeyLabels = JourneyLabels(
      en = LanguageLabels(
        appLevelLabels(english),
        selectPageLabels(english),
        lookupPageLabels(english),
        editPageLabels(english),
        confirmPageLabels(english)
      ),
      cy = LanguageLabels(
        appLevelLabels(welsh),
        selectPageLabels(welsh),
        lookupPageLabels(welsh),
        editPageLabels(welsh),
        confirmPageLabels(welsh)
      )
    )

    AlfJourneyConfig(
      version = AlfJourneyConfig.defaultConfigVersion,
      options = journeyOptions,
      labels = journeyLabels
    )

  }
}
