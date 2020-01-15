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

package forms

import helpers.FormTestHelpers
import play.api.data.FormError
import uk.gov.hmrc.play.test.UnitSpec

class QuestionnaireFormSpec extends UnitSpec with FormTestHelpers {


  def questionnaireMap(ableToAchieve:String="foo1",
                       whyNotAchieve:String ="foo2",
                       tryingToDo:String="foo3",
                       meetNeeds:String="1",
                       reccommendation:String="foo4",
                       satisfaction:String = "foo5",
                       improvements:String = "improve") = {

    Map(
      "ableToAchieve" -> ableToAchieve,
      "whyNotAchieve" -> whyNotAchieve,
      "meetNeeds" -> meetNeeds,
      "tryingToDo" -> tryingToDo,
      "satisfaction" -> satisfaction,
      "recommendation" -> reccommendation,
      "improvements" -> improvements
    )

  }
  "Questionnaire form" should {

    val form = QuestionnaireForm.form

    "not contain any errors for valid data" in {
      assertFormSuccess(form, questionnaireMap())
    }

    "contain errors when a blank ableToAchieve rating is provided" in {

      bindFromRequestWithErrors(questionnaireMap(ableToAchieve = "").toList, form).map(x => (x.key, x.message)) shouldBe
        List(("ableToAchieve", "error.required"))
    }

    "contain errors when invalid meetNeeds is provided " in {
      bindFromRequestWithErrors(questionnaireMap(meetNeeds = "0").toList, form).map(x => (x.key, x.message)) shouldBe
        List(("meetNeeds", "error.min"))
    }
    "contain errors when blank tryingToDo is provided " in {
      bindFromRequestWithErrors(questionnaireMap(tryingToDo = "").toList, form).map(x => (x.key, x.message)) shouldBe
        List(("tryingToDo", "error.required"))
    }
    "contain errors when blank satisfaction is provided " in {

      val expectedError = Seq(FormError("satisfaction", "error.required"))
      assertFormError(form, questionnaireMap(satisfaction = ""), expectedError)
    }

     "contain errors when blank recommendation is provided " in {

      val expectedError = Seq(FormError("recommendation", "error.required"))
      assertFormError(form, questionnaireMap(reccommendation = ""), expectedError)
    }
  }
}