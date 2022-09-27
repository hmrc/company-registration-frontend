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
import helpers.UnitSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import views.BaseSelectors
import views.html.errors.takeoverInformationNeeded

class TakeoverInformationNeededViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  object Selectors extends BaseSelectors

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  "TakeoverInformationNeededView" should {
    lazy val page = app.injector.instanceOf[takeoverInformationNeeded]
    lazy val view = page()
    lazy val doc = Jsoup.parse(view.body)

    lazy val title = "We need some additional information"
    lazy val heading = "We need some additional information"
    lazy val paragraph = "Before you continue with the registration, we need some more information about your company taking over another company."
    lazy val continue = "Continue"

    s"have an expected title: $title" in {
      doc.title must include(title)
    }

    s"have an expected heading: $heading" in {
      doc.selectFirst("h1").text mustBe heading
    }

    s"have an expected paragraph: $paragraph" in {
      doc.select(Selectors.p(1)).text mustBe paragraph
    }

    s"have a $continue button" in {
      doc.getElementById("continue").text() mustBe continue
    }
  }
}
