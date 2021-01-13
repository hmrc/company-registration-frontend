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

package views

import _root_.helpers.SCRSSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html

class MainTemplateSpec extends SCRSSpec with GuiceOneAppPerSuite {

  val fakeTitle = "Fake Title"

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val messages = app.injector.instanceOf[MessagesApi].preferred(request)


  "main template" should {
    "append the title with the service name and GOV.UK" in {
      val view = views.html.main_template(fakeTitle)(Html(""))
      val doc = Jsoup.parse(view.toString())
      doc.title shouldBe s"$fakeTitle - Set up a limited company and register for Corporation Tax - GOV.UK"
    }
  }
}
