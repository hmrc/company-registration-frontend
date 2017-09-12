/*
 * Copyright 2017 HM Revenue & Customs
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


import models.{AboutYouChoice, AboutYouChoiceForm, EmptyStringValidator}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.Lang
import uk.gov.voa.play.form.ConditionalMappings._
import utils.SCRSValidators.completionCapacityValidation

object AboutYouForm extends EmptyStringValidator {

  private def ifOther(mapping: Mapping[String]): Mapping[String] = onlyIf(isEqual("completionCapacity", "other"), mapping)("")


  def form = Form(
    mapping(
      "completionCapacity" -> customErrorTextValidation,
      "completionCapacityOther" -> ifOther(text.verifying(completionCapacityValidation))
    )(AboutYouChoiceForm.apply)(AboutYouChoiceForm.unapply)
  )

  def aboutYouFilled = form.fill(AboutYouChoiceForm("", ""))

  def populateForm(capcity : String) : Form[AboutYouChoiceForm] = {
    capcity match {
      case "director" => form.fill(AboutYouChoiceForm(capcity, ""))
      case "agent" => form.fill(AboutYouChoiceForm(capcity, ""))
      case "" => form.fill(AboutYouChoiceForm("", ""))
      case _ => form.fill(AboutYouChoiceForm("other", capcity))
    }
  }

  def endpointForm(implicit lang:Lang) = Form(
    mapping(
      "completionCapacity" -> text
    )(AboutYouChoice.apply)(AboutYouChoice.unapply)
  )
}

