/*
 * Copyright 2019 HM Revenue & Customs
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

import models.{EmptyStringValidator, GroupUTR}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import uk.gov.voa.play.form.ConditionalMappings._
import utils.SCRSValidators._

object GroupUtrForm extends EmptyStringValidator {

  private def unapplyGUTR(groupUTR: GroupUTR): Option[(String, Option[String])] = {
    val firstRadioButtonValue = groupUTR.UTR.fold("noutr")(_ => "utr")
    Some(firstRadioButtonValue, groupUTR.UTR)
  }
  val radioButtonvalidation = (radioValue:String) => Seq("utr","noutr").contains(radioValue)
  private def ifOther(mapping: Mapping[String]): Mapping[Option[String]] = mandatoryIfEqual("groupUtr", "utr", mapping)
  def form: Form[GroupUTR] = Form(
    mapping(
      "groupUtr" -> customErrorTextValidation.verifying("error.groupUtr.required", radioButtonvalidation),
      "utr" -> ifOther(text.verifying(UtrValidation))
    )((gUtr, utr) => GroupUTR(utr))(unapplyGUTR)
  )


}