/*
 * Copyright 2022 HM Revenue & Customs
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

package views.takeovers

import config.AppConfig
import forms.takeovers.WhoAgreedTakeoverForm
import helpers.UnitSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import views.BaseSelectors
import views.html.takeovers.WhoAgreedTakeover

class WhoAgreedTakeoverViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  object Selectors extends BaseSelectors

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]
  lazy val testPreviousOwnerName: String = "testName"

  "OtherBusinessNameView" should {
    lazy val form = WhoAgreedTakeoverForm.form
    lazy val page = app.injector.instanceOf[WhoAgreedTakeover]
    lazy val view = page(form, testPreviousOwnerName)
    lazy val doc = Jsoup.parse(view.body)

    lazy val title = s"Who agreed the takeover on behalf of $testPreviousOwnerName?"
    lazy val heading = s"Who agreed the takeover on behalf of $testPreviousOwnerName?"
    lazy val line1 = "If you’re changing:"
    lazy val bullet1 = "your sole trader business into a limited company, give your own name"
    lazy val bullet2 = "a business partnership into a limited company, give the nominated partner’s name"
    lazy val field = "Enter name"
    lazy val saveAndContinue = "Save and continue"

    s"have an expected title: $title" in {
      doc.title should include(title)
    }

    s"have an expected heading: $heading" in {
      doc.selectFirst("h1").text shouldBe heading
    }

    s"have an expected paragraph: $line1" in {
      doc.select(Selectors.p(1)).text shouldBe line1
    }

    s"have an expected input form: $field" in {
      doc.selectFirst("label").text shouldBe field
    }

    s"have a $saveAndContinue button" in {
      doc.getElementById("continue").text() shouldBe saveAndContinue
    }
  }
}
