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

import forms.templates.PhoneNoForm
import models.CompanyContactDetailsApi
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Lang
import utils.SCRSValidators.{companyContactDetailsValidation, emailValidation}


object CompanyContactForm extends PhoneNoForm {
  def form(implicit lang:Lang) = Form(
    mapping(
      "contactEmail" -> optional(text.verifying(emailValidation)),
      "contactDaytimeTelephoneNumber" -> phoneNoField,
      "contactMobileNumber" -> phoneNoField
    )(CompanyContactDetailsApi.apply)(CompanyContactDetailsApi.unapply).verifying(companyContactDetailsValidation)
  )
}
