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

package views.takeovers

import config.FrontendAppConfig
import forms.takeovers.OtherBusinessNameForm
import helpers.UnitSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import views.html.takeovers.OtherBusinessName

class OtherBusinessNameViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val frontendAppConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  implicit lazy val page = app.injector.instanceOf[OtherBusinessName]

  "OtherBusinessNameView" should {
    lazy val form = OtherBusinessNameForm.form
    lazy val view = page(form)
    lazy val doc = Jsoup.parse(view.body)

    lazy val title = "What is the name of the other business?"
    lazy val heading = "What is the name of the other business?"
    lazy val paragraph = "Give the name that’s registered with HMRC for tax purposes."
    lazy val inputField = "Enter the name"
    lazy val saveAndContinue = "Save and continue"

    s"have an expected title: $title" in {
      doc.title should include(title)
    }

    s"have an expected heading: $heading" in {
      doc.selectFirst("h1").text shouldBe heading
    }

    s"have an expected paragraph: $paragraph" in {
      doc.getElementById("line1").text shouldBe paragraph
    }

    s"have an expected input form: $inputField" in {
      doc.selectFirst("label").text shouldBe inputField
    }

    s"have a $saveAndContinue button" in {
      doc.selectFirst("input.button").attr("value") shouldBe saveAndContinue
    }
  }
}
