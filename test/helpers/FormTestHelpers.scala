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

package helpers

import play.api.data.{Form, FormError}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec

trait FormTestHelpers {
  _: UnitSpec =>

  def bindFromRequestWithErrors(formData: Seq[(String, String)], form: Form[_]): Seq[FormError] = {
    bind(formData, form).left.get.get
  }

  private[helpers] def bind(formData: Seq[(String, String)], form: Form[_]): Either[Option[Seq[FormError]], _] = {
    val request = FakeRequest().withFormUrlEncodedBody(formData: _*)
    form.bindFromRequest()(request).fold(
      hasErrors => Left(Some(hasErrors.errors)),
      success => Right(Some(success))
    )
  }

  def bindSuccess(request: FakeRequest[AnyContentAsFormUrlEncoded], form: Form[_]): Some[_] = {
    form.bindFromRequest()(request).fold(
      formWithErrors => Some(formWithErrors.errors(0)),
      userData => Some(userData)
    )
  }

  def bindWithError(request: FakeRequest[AnyContentAsFormUrlEncoded], form: Form[_]): Some[_] = {
    form.bindFromRequest()(request).fold(
      formWithErrors => Some(formWithErrors.errors(0)),
      userData => Some(None)
    )
  }

  def assertFormSuccess(form: Form[_], data: Map[String, String]) = {
    form.bind(data).fold(
      formWithErrors => {
        fail(s"Expected the form to bind successfully, but found the following errors: ${formWithErrors.errors.map(errs => (errs.key, errs.messages))}.")
      },
      _ => assert(true)
    )
  }

  def assertFormError(form: Form[_], data: Map[String, String], expectedErrors: Seq[FormError]) = {
    form.bind(data).fold(
      formWithErrors => {
        formWithErrors.errors shouldBe expectedErrors
      },
      _ => fail("Expected a validation error when binding the form, but it was bound successfully.")
    )
  }
}
