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

package forms

import models._
import play.api.data.Form
import play.api.data.Forms._

object CompanyDetailsForm {
  def form() = Form(
    mapping(
      "companyName" -> text,
      "chROAddress" -> mapping(
        "premises" -> text,
        "address_line_1" -> text,
        "address_line_2" -> optional(text),
        "locality" -> text,
        "country" -> text,
        "po_box" -> optional(text),
        "postal_code" -> optional(text),
        "region" -> optional(text)
      )(CHROAddress.apply)(CHROAddress.unapply),
      "pPOBAddress" -> mapping(
        "type" -> default(text, "DEFAULT_SET_IN_FORM"),//todo - SCRS-3708: fix default
        "address" -> optional(mapping(
        "houseNameNumber" -> optional(text),
        "addressLine1" -> text,
        "addressLine2" -> text,
        "addressLine3" -> optional(text),
        "addressLine4" -> optional(text),
        "postCode" -> optional(text),
        "country" -> optional(text),
        "uprn" -> optional(text),
        "txid" -> default[String](text, Address.generateTxId),
        "auditRef" -> default[Option[String]](optional(text), None)
        )(Address.apply)(Address.unapply))
      )(PPOB.apply)(PPOB.unapply),
      "jurisdiction" -> text
    )(CompanyDetails.apply)(CompanyDetails.unapply)
  )
}
