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

package helpers

import play.api.libs.json.{JsError, JsPath, JsResult, JsSuccess, JsonValidationError}

trait JsonValidation {
  this: UnitSpec =>

  def mustBeSuccess[T](expected: T, result: JsResult[T]) = {
    result match {
      case JsSuccess(v, path) => v mustBe expected
      case JsError(errors) => fail(s"Test produced errors - $errors")
    }
  }

  def shouldHaveErrors[T](result: JsResult[T], errorPath: JsPath, expectedError: JsonValidationError): Unit = {
    shouldHaveErrors[T](result, Map(errorPath -> Seq(expectedError)))
  }

  def shouldHaveErrors[T](result: JsResult[T], errorPath: JsPath, expectedErrors: Seq[JsonValidationError]): Unit = {
    shouldHaveErrors[T](result, Map(errorPath -> expectedErrors))
  }

  def shouldHaveErrors[T](result: JsResult[T], expectedErrors: Map[JsPath, Seq[JsonValidationError]]): Unit = {
    result match {
      case JsSuccess(v, path) => fail(s"read should have failed and didn't - produced $v")
      case JsError(errors) =>
        errors.length mustBe expectedErrors.keySet.toSeq.length

        for (error <- errors) {
          error match {
            case (path, valErrs) =>
              expectedErrors.keySet must contain(path)
              expectedErrors(path) mustBe valErrs
          }
        }
    }
  }
}