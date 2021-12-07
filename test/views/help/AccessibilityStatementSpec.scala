/*
 * Copyright 2021 HM Revenue & Customs
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

package views.help

import helpers.SCRSSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import views.html.help.accessibility_statement

class AccessibilityStatementSpec extends SCRSSpec with GuiceOneAppPerSuite {

  object Messages {
    val title = "Accessibility statement for setting up a limited company and registering for corporation tax"
    val heading = "Accessibility statement for setting up a limited company and registering for corporation tax"
    val introP1 = "This accessibility statement explains how accessible this service is, what to do if you have difficulty using it, and how to report accessibility problems with the service."
    val introP2 = "This service is part of the wider GOV.UK website. There is a separate accessibility statement for the main GOV.UK website."
    def introP3(serviceStartUrl: String) = s"This page only contains information about the setting up a limited company and registering for corporation tax service, available at $serviceStartUrl"
    val serviceH2 = "Using this service"
    val serviceP1 = "This service allows businesses to set up a limited company and register for corporation tax."
    val serviceP2 = "This service is run by HM Revenue and Customs (HMRC). We want as many people as possible to be able to use this service. This means you should be able to:"
    val serviceLi1 = "change colours, contrast levels and fonts"
    val serviceLi2 = "zoom in up to 300% without the text spilling off the screen"
    val serviceLi3 = "get from the start of the service to the end using just a keyboard"
    val serviceLi4 = "get from the start of the service to the end using speech recognition software"
    val serviceLi5 = "listen to the service using a screen reader (including the most recent versions of JAWS, NVDA and VoiceOver)"
    val serviceP3 = "We have also made the text in the service as simple as possible to understand."
    val serviceP4 = "AbilityNet has advice on making your device easier to use if you have a disability."
    val howAccessH2 = "How accessible this service is"
    val howAccessP1 = "This service is partially compliant with the Web Content Accessibility Guidelines version 2.1 AA standard."
    val howAccessP2 = "Some people may find parts of this service difficult to use:"
    val howAccessB1 = "The menu does not behave as expected when navigating iOS."
    val howAccessB2 = "The colour contrast fails to meet WCAG 2.1 AA guidelines for contract minimum."
    val reportProblemH2 = "Reporting accessibility problems with this service"
    val reportProblemP1 = "We are always looking to improve the accessibility of this service. If you find any problems that are not listed on this page or think we are not meeting accessibility requirements, report the accessibility problem."
    val howToDoH2 = "What to do if you are not happy with how we respond to your complaint"
    val whatToDoP1 = "The Equality and Human Rights Commission (EHRC) is responsible for enforcing the Public Sector Bodies (Websites and Mobile Applications) (No. 2) Accessibility Regulations 2018 (the ’accessibility regulations’). If you are not happy with how we respond to your complaint, contact the Equality Advisory and Support Service (EASS), or the Equality Commission for Northern Ireland (ECNI) if you live in Northern Ireland."
    val contactUsH2 = "Contacting us by phone or getting a visit from us in person"
    val contactUsP1 = "We provide a text relay service if you are deaf, hearing impaired or have a speech impediment."
    val contactUsP2 = "We can provide a British Sign Language (BSL) interpreter, or you can arrange a visit from an HMRC advisor to help you complete the service."
    val contactUsP3 = "Find out how to contact us."
    val technicalInfoH2 = "Technical information about this service’s accessibility"
    val technicalInfoP1 = "HMRC is committed to making this service accessible, in accordance with the Public Sector Bodies (Websites and Mobile Applications) (No. 2) Accessibility Regulations 2018."
    val technicalInfoP2 = "This service is partially compliant with the Web Content Accessibility Guidelines version 2.1 AA standard, due to the non-compliances listed below."
    val technicalInfoH3 = "Non‐accessible content"
    val technicalInfoP3 = "The content listed below is non-accessible for the following reasons."
    val technicalInfoH4 = "Non‐compliance with the accessibility regulations"
    val technicalInfoP4 = "The legend for the date inputs is unclear for screen reader users. This means screen reader users do not receive the information contained in the visible label. This will be fixed by 28 January 2022."
    val technicalInfoP5 = "When an error is committed in the form field, the error handling fails to meet WCAG 2.1 guidelines for error identification. This will be fixed by 28 January 2022."
    val technicalInfoP6 = "The menu does not behave as expected when navigating using iOS. Screen reader users may find this disorientating as there is no audible cue to announce that the menu has expanded and users are unable to access the ‘Sign in’ link or go back to the ‘Menu’ link as it disappears. This will be fixed by 28 January 2022."
    val technicalInfoP7 = "An invalid role has been assigned to the list element. This issue may affect how screen reading software reads the list as the list has been structured incorrectly. This will be fixed by 28 January 2022."
    val technicalInfoP8 = "The colour contrast fails to meet WCAG 2.1 AA guidelines for contrast minimum. This issue could affect low-vision users navigating the service who may have difficulty in distinguishing the white link text against the background colour. To meet AA guidelines, the contrast ratio should be at least 4.5.1 for regular sized text (it is currently 1.6:1). This will be fixed by 28 January 2022."
    val technicalInfoP9 = "When a link receives focus it fails to meet WCAG AA guidelines for contrast minimum. To meet AA guidelines to contrast ratio should be at least 4.3.1 for regular sized text (it is currently 1.6:1). This will be fixed by 28 January 2022."
    val technicalInfoP10 = "An input label is non-descriptive and screen reader users navigating the input page, using the form fields dialog list may find difficulty in understanding the purpose of the input field. This will be fixed by 28 January 2022."
    val technicalInfoP11 = "When the page was set to reflow settings (1280 CSS pixels wide at 400% zoom), the text adjacent to the radio button spills off the page. This issue may affect low-vision users who use reflow settings to view web pages from being able to clearly read the content. This will be fixed by 28 January 2022."
    val technicalInfoP12 = "The legend for the date inputs is unclear for screen reader users. This means screen reader users do not receive the information contained in the visible label which instructs the user. This will be fixed by 28 January 2022."
    val howWeTestH2 = "How we tested this service"
    val howWeTestP1 = "The service was last tested on 31 August 2021 and was checked for compliance with WCAG 2.1 AA."
    val howWeTestP2 = "The service was built using parts that were tested by the Digital Accessibility Centre. The full service was tested by HMRC and included disabled users."
    val howWeTestP3 = "This page was prepared on 25 November 2021. It was last updated on 7 December 2021."


    val accessibilityStatementLinkText = "accessibility statement"
    val abilityNetLinkText = "AbilityNet"
    val wcagLinkText = "Web Content Accessibility Guidelines version 2.1 AA standard"
    val contactUsLinkText = "contact us"
    val reportProblemLinkText = "accessibility problem"
    val eassLinkText = "contact the Equality Advisory and Support Service"
    val ecniLinkText = "Equality Commission for Northern Ireland"
    val dacLinkText = "Digital Accessibility Centre"
    val accessibilityLink = "Accessibility"

  }

  val testAccessibilityReportUrl = "testAccessibilityReportUrl"
  val testServiceStartUrl = "testServiceStartUrl"

  lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  lazy val page: HtmlFormat.Appendable = accessibility_statement(testAccessibilityReportUrl, testServiceStartUrl)(
    request,
    messagesApi.preferred(request),
    mockAppConfig
  )


  "The accessibility page" should {
    lazy val parsePage = Jsoup.parse(page.body)
    lazy val pageBody = parsePage.getElementById("content")


    "have a heading" in {
      pageBody.select("h1").text shouldBe Messages.heading
    }

    "have a title" in {
      parsePage.title should include(Messages.title)
    }

    "have multiple h2" in {
      pageBody.select("h2").eachText() should contain allElementsOf Seq(
        Messages.serviceH2,
        Messages.howAccessH2,
        Messages.reportProblemH2,
        Messages.howToDoH2,
        Messages.contactUsH2,
        Messages.technicalInfoH2,
        Messages.howWeTestH2
      )
    }

    "have multiple h3" in {
      pageBody.select("h3").eachText() should contain allElementsOf Seq(
        Messages.technicalInfoH3,
        Messages.technicalInfoH4
      )
    }

    "have multiple bullet points" in {
      pageBody.select("li").eachText() should contain allElementsOf Seq(
        Messages.serviceLi1,
        Messages.serviceLi2,
        Messages.serviceLi3,
        Messages.serviceLi4,
        Messages.serviceLi5,
        Messages.howAccessB1,
        Messages.howAccessB2
      )
    }

    "have multiple paragraphs" in {
      pageBody.select("p").eachText should contain allElementsOf Seq(
        Messages.introP1,
        Messages.introP2,
        Messages.introP3(testServiceStartUrl),
        Messages.serviceP1,
        Messages.serviceP2,
        Messages.serviceP3,
        Messages.serviceP4,
        Messages.howAccessP1,
        Messages.howAccessP2,
        Messages.reportProblemP1,
        Messages.whatToDoP1,
        Messages.contactUsP1,
        Messages.contactUsP2,
        Messages.contactUsP3,
        Messages.technicalInfoP1,
        Messages.technicalInfoP2,
        Messages.technicalInfoP3,
        Messages.technicalInfoP4,
        Messages.technicalInfoP5,
        Messages.technicalInfoP6,
        Messages.technicalInfoP7,
        Messages.technicalInfoP8,
        Messages.technicalInfoP9,
        Messages.technicalInfoP10,
        Messages.technicalInfoP11,
        Messages.technicalInfoP12,
        Messages.howWeTestP1,
        Messages.howWeTestP2,
        Messages.howWeTestP3
      )
    }

    "have a link to the govuk accessibility statement" in {
      pageBody.select("a[href=https://www.gov.uk/help/accessibility]").text shouldBe Messages.accessibilityStatementLinkText
    }

    "have a link to the service" in {
      pageBody.select(s"a[href=$testServiceStartUrl]").text shouldBe testServiceStartUrl
    }

    "have a link to ability net" in {
      pageBody.select("a[href=https://mcmw.abilitynet.org.uk/]").text shouldBe Messages.abilityNetLinkText
    }

    "have two links to the accessibility guidelines" in {
      pageBody.select("a[href=https://www.w3.org/TR/WCAG21/]").text shouldBe Seq(
        Messages.wcagLinkText,
        Messages.wcagLinkText
      ).mkString(" ")
    }

    "have a link to contact us" in {
      pageBody.select("a[href=https://www.gov.uk/dealing-hmrc-additional-needs]").text shouldBe Messages.contactUsLinkText
    }

    "have a link to reporting problem form" in {
      pageBody.select(s"a[href=$testAccessibilityReportUrl]").text shouldBe Messages.reportProblemLinkText
    }

    "have a link to EASS" in {
      pageBody.select("a[href=https://www.equalityadvisoryservice.com/]").text shouldBe Messages.eassLinkText
    }

    "have a link to ECNI" in {
      pageBody.select("a[href=https://www.equalityni.org/Home]").text shouldBe Messages.ecniLinkText
    }

    "have a link to DAC" in {
      pageBody.select("a[href=http://www.digitalaccessibilitycentre.org/]").text shouldBe Messages.dacLinkText
    }

  }
}
