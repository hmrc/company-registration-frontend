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

package views.takeovers

import config.FrontendAppConfig
import forms.takeovers.OtherBusinessAddressForm
import models.NewAddress
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class OtherBusinessAddressViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {

  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit lazy val frontendAppConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

  val testBusinessName: String = "testName"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))

  "OtherBusinessAddressView" should {
    lazy val form = OtherBusinessAddressForm.form(testBusinessName, 1)
    lazy val view = views.html.takeovers.OtherBusinessAddress(form, testBusinessName, Seq(testBusinessAddress))
    lazy val doc = Jsoup.parse(view.body)

    lazy val title = s"What is $testBusinessName’s address?"
    lazy val heading = s"What is $testBusinessName’s address?"
    lazy val line1 = s"If $testBusinessName is:"
    lazy val bullet1 = "a company, use the address that’s on its Companies House record"
    lazy val bullet2 = "a sole trader or business partnership, use the address that it has registered for Income Tax"
    lazy val address = testBusinessAddress.mkString
    lazy val otherAddress = "A different address"
    lazy val saveAndContinue = "Save and continue"

    s"have an expected title: $title" in {
      doc.title() shouldBe title
    }

    s"have an expected heading: $heading" in {
      doc.selectFirst("h1").text shouldBe heading
    }

    s"have an expected paragraph: $line1" in {
      doc.getElementById("line1").text shouldBe line1
    }

    s"have an expected bullet list" in {
      val list = doc.getElementById("paragraph-one").select("ul").select("li")
      list.get(0).text shouldBe bullet1
      list.get(1).text shouldBe bullet2
    }

    s"have expected radio labels: $address and $otherAddress" in {
      val list = doc.select("label")
      list.get(0).text shouldBe address
      list.get(1).text shouldBe otherAddress
    }

    s"have a $saveAndContinue button" in {
      doc.selectFirst("input.button").attr("value") shouldBe saveAndContinue
    }
  }
}
