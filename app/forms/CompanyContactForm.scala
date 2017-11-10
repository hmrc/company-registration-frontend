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

import forms.templates.PhoneNoForm
import models.CompanyContactViewModel
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.nonEmpty
import play.api.i18n.Lang
import uk.gov.hmrc.play.mappers.StopOnFirstFail
import utils.SCRSValidators._

object CompanyContactForm extends PhoneNoForm {
  def form(implicit lang:Lang) = Form(
    mapping(
      "contactName" -> text.verifying(StopOnFirstFail(nonEmpty, contactNameValidation)),
      "contactEmail" -> optional(text.verifying(emailValidation)),
      "contactDaytimeTelephoneNumber" -> phoneNoField,
      "contactMobileNumber" -> phoneNoField
    )(CompanyContactViewModel.apply)(CompanyContactViewModel.unapply).verifying(companyContactDetailsValidation)
  )
}
