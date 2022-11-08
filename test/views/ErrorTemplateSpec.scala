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

package views

import config.AppConfig
import helpers.SCRSSpec
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, MessagesApi}

class ErrorTemplateSpec extends SCRSSpec with GuiceOneAppPerSuite {

  val errorTemplate = app.injector.instanceOf[views.html.error_template]
  val appConfig = app.injector.instanceOf[AppConfig]
  val messages = app.injector.instanceOf[MessagesApi].preferred(Seq(Lang("en")))

  val errorPage = errorTemplate("Title", "Heading", "Msg")(fakeRequest(), messages, appConfig)
  val document = Jsoup.parse(errorPage.toString)

  "Rendering the errorTemplate" must {

    "have the correct Title" in {
      document.title mustBe "Title"
    }

    "have the correct Heading" in {
      document.select("h1").text mustBe "Heading"
    }

    "have the correct Error Message" in {
      document.select("main p").text mustBe "Msg"
    }
  }

}
