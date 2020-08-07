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
import forms.takeovers.ReplacingAnotherBusinessForm
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

class ReplacingAnotherBusinessViewSpec extends UnitSpec with GuiceOneAppPerSuite with I18nSupport {
  implicit lazy val messagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val request = FakeRequest()
  implicit lazy val frontendAppConfig = app.injector.instanceOf[FrontendAppConfig]

  "ReplacingAnotherBusinessView" should {
    lazy val form = ReplacingAnotherBusinessForm.form
    lazy val view = views.html.takeovers.ReplacingAnotherBusiness(form)
    lazy val doc = Jsoup.parse(view.body)

    val expectedTitle = "Is the new company replacing another business?"
    s"have a title of '$expectedTitle'" in {
      doc.title should include(expectedTitle)
    }

    lazy val paragraph1 = doc.getElementById("paragraph-one")
    "have a section which" should {
      val expectedP1 = "This includes if it’s:"
      s"have a paragraph with '$expectedP1'" in {
        paragraph1.selectFirst("p").text() shouldBe expectedP1
      }

      lazy val bulletList = paragraph1.select("ul")
      s"have a bullet point list which " should {
        val expectedBulletPointClass = "list-bullet"
        s"have a class of $expectedBulletPointClass" in {
          bulletList.attr("class") shouldBe expectedBulletPointClass
        }

        lazy val bulletPoints = bulletList.select("li")
        val expectedBullet1 = "buying another company"
        s"have a bullet with '$expectedBullet1'" in {
          bulletPoints.get(0).text shouldBe expectedBullet1
        }

        val expectedBullet2 = "changing from a sole trader or business partnership to a limited company"
        s"have a bullet with '$expectedBullet2'" in {
          bulletPoints.get(1).text shouldBe expectedBullet2
        }
      }
    }

    lazy val accordion = doc.select("details")
    "have an accordion which" should {
      val expectedAccordionSummary = "What is a sole trader or business partnership?"
      s"have a title of '$expectedAccordionSummary'" in {
        accordion.select("summary").text shouldBe expectedAccordionSummary
      }

      val expectedAccordionP1 = "A sole trader is someone who’s self-employed and is the only owner of their business."
      s"have a paragraph of '$expectedAccordionP1'" in {
        accordion.select("p").get(0).text shouldBe expectedAccordionP1
      }

      val expectedAccordionP2 = "A business partnership is when two or more people agree to share the profits, costs and risks of running a business."
      s"have a paragraph of '$expectedAccordionP2'" in {
        accordion.select("p").get(1).text shouldBe expectedAccordionP2
      }
    }

    lazy val radioForm = doc.select("fieldset")
    val fieldsetInlineClass = "inline"
    s"have a class of $fieldsetInlineClass" in {
      radioForm.attr("class") shouldBe fieldsetInlineClass
    }
    "have a radio button form which" should {
      val expectedYesOption = "Yes"
      s"have a '$expectedYesOption' option" in {
        radioForm.select("label").get(0).text shouldBe expectedYesOption
      }

      val expectedNoOption = "No"
      s"have a '$expectedNoOption' option" in {
        radioForm.select("label").get(1).text shouldBe expectedNoOption
      }
    }

    lazy val saveAndContinue = doc.select("input.button")
    val expectedSaveAndContinueButton = "Save and continue"
    s"have a '$expectedSaveAndContinueButton' button'" in {
      saveAndContinue.attr("value") shouldBe expectedSaveAndContinueButton
    }
  }
}
