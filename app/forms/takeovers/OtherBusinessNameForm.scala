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

package forms.takeovers

import models.EmptyStringValidator
import play.api.data.Form
import play.api.data.Forms._
import utils.SCRSValidators._


object OtherBusinessNameForm extends EmptyStringValidator {

  val otherBusinessNameKey = "otherBusinessName"

  def nameUnapply(name: String): Option[String] =
    if (name.isEmpty) None else Some(name)

  def form: Form[String] = Form(
    mapping(
      otherBusinessNameKey -> text.verifying(otherBusinessNameValidation)
    )(identity)(nameUnapply)
  )
}
