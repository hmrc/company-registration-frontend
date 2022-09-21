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
import forms.takeovers.ReplacingAnotherBusinessForm
import helpers.UnitSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.test.FakeRequest
import views.BaseSelectors
import views.html.takeovers.ReplacingAnotherBusiness

class ReplacingAnotherBusinessViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  object Selectors extends BaseSelectors

  implicit lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request = FakeRequest()
  implicit lazy val appConfig = app.injector.instanceOf[AppConfig]
  implicit lazy val page = app.injector.instanceOf[ReplacingAnotherBusiness]

  "ReplacingAnotherBusinessView" should {
    lazy val form = ReplacingAnotherBusinessForm.form
    lazy val view = page(form)
    lazy val doc = Jsoup.parse(view.body)

    val expectedTitle = "Is the new company replacing another business?"
    s"have a title of '$expectedTitle'" in {
      doc.title should include(expectedTitle)
    }

    "have a section which" should {
      val expectedP1 = "This includes if it’s: A sole trader is someone who’s self-employed and is the only owner of their business."
      s"have a paragraph with '$expectedP1'" in {
        doc.select(Selectors.p(1)).text() shouldBe expectedP1
      }
    }

    lazy val saveAndContinue = doc.getElementById("continue")
    val expectedSaveAndContinueButton = "Save and continue"
    s"have a '$expectedSaveAndContinueButton' button'" in {
      saveAndContinue.text() shouldBe expectedSaveAndContinueButton
    }
  }
}
