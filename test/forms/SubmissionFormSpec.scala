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

package forms

import helpers.FormTestHelpers
import play.api.data.FormError
import uk.gov.hmrc.play.test.UnitSpec

class SubmissionFormSpec extends UnitSpec with FormTestHelpers {

  "Submission form" should {

    val form = SubmissionForm.form

    "not contain any errors for valid data" in {
      val data = Map(
        "submissionStatus" -> "testSubmissionStatus",
        "submissionRef" -> "testSubmissionRef"
      )

      assertFormSuccess(form, data)
    }

    "contain errors when a blank submission status is provided" in {
      val data = Map(
        "submissionStatus" -> "",
        "submissionRef" -> "testSubmissionRef"
      )

      val expectedError = Seq(FormError("submissionStatus", "error.required"))

      assertFormError(form, data, expectedError)
    }

    "contain errors when a blank submission ref is provided" in {
      val data = Map(
        "submissionStatus" -> "testSubmissionStatus",
        "submissionRef" -> ""
      )

      val expectedError = Seq(FormError("submissionRef", "error.required"))

      assertFormError(form, data, expectedError)
    }
  }
}