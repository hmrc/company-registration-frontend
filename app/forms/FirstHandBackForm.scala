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

import models.CHROAddress
import models.handoff.CompanyNameHandOffFormModel
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}

object FirstHandBackForm {
  def form() = Form(
    mapping(
      "journey_id" -> optional(text),
      "user_id" -> text,
      "company_name" -> text,
      "registered_office_address" -> mapping(
        "premises" -> text,
        "address_line_1" -> text,
        "address_line_2" -> optional(text),
        "locality" -> text,
        "country" -> text,
        "po_box" -> optional(text),
        "postal_code" -> optional(text),
        "region" -> optional(text)
      )(CHROAddress.apply)(CHROAddress.unapply),
      "jurisdiction" -> text,
      "ch" -> text,
      "hmrc" -> text
    )(CompanyNameHandOffFormModel.apply)(CompanyNameHandOffFormModel.unapply)
  )
}
