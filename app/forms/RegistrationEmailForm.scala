/*
 * Copyright 2023 HM Revenue & Customs
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

import models.{EmptyStringValidator, RegistrationEmailModel}
import play.api.data.Forms._
import play.api.data.{Form, Mapping}
import uk.gov.voa.play.form.ConditionalMappings._
import utils.SCRSValidators._


object RegistrationEmailForm extends  EmptyStringValidator {

  private def ifOther(mapping: Mapping[String]): Mapping[Option[String]] = mandatoryIfEqual("registrationEmail", "differentEmail", mapping)

  val radioButtonvalidation = (radioValue:String) => Seq("currentEmail","differentEmail").contains(radioValue)
  def form = Form(
    mapping(
      "registrationEmail" -> customErrorTextValidation.verifying("error.registrationEmail.required", radioButtonvalidation),
      "DifferentEmail" -> ifOther(customErrorTextValidation.verifying(emailValidation))
    )(RegistrationEmailModel.apply)(RegistrationEmailModel.unapply)
  )
}
