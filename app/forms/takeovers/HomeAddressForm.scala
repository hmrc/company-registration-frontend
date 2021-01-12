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

package forms.takeovers

import forms.takeovers.formatters.AddressChoiceFormatter.addressChoiceFormatter
import models.EmptyStringValidator
import models.takeovers.AddressChoice
import play.api.data.Form
import play.api.data.Forms._

object HomeAddressForm extends EmptyStringValidator {

  val homeAddressKey = "homeAddress"
  val homeAddressPageKey = "homeAddress"

  def form(addressCount: Int): Form[AddressChoice] = Form(
    single(homeAddressKey -> of[AddressChoice](addressChoiceFormatter(
      optName = None,
      addressCount = addressCount,
      pageSpecificKey = homeAddressPageKey
    )))
  )

}