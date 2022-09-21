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
import forms.takeovers.OtherBusinessAddressForm
import helpers.UnitSpec
import models.NewAddress
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import views.BaseSelectors
import views.html.takeovers.OtherBusinessAddress

class OtherBusinessAddressViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  object Selectors extends BaseSelectors

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  val testBusinessName: String = "testName"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))

  val page = app.injector.instanceOf[OtherBusinessAddress]

  "OtherBusinessAddressView" should {
    lazy val form = OtherBusinessAddressForm.form(testBusinessName, 1)
    lazy val view = page(form, testBusinessName, Seq(testBusinessAddress))
    lazy val doc = Jsoup.parse(view.body)

    lazy val title = s"What is $testBusinessName’s address?"
    lazy val heading = s"What is $testBusinessName’s address?"
    lazy val line1 = s"If $testBusinessName is:"
    lazy val bullet1 = "a company, use the address that’s on its Companies House record"
    lazy val bullet2 = "a sole trader or business partnership, use the address that it has registered for Income Tax"
    lazy val address = testBusinessAddress.toString
    lazy val otherAddress = "A different address"
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

    s"have a $saveAndContinue button" in {
      doc.getElementById("continue").text() shouldBe saveAndContinue
    }
  }
}
