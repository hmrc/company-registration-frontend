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

import models.{AccountingDatesModel, EmptyStringValidator}
import org.joda.time.LocalDate
import play.api.data.Form
import play.api.data.Forms._
import services.TimeService
import uk.gov.hmrc.play.mappers.StopOnFirstFail
import utils.{SCRSValidatorsT, SystemDate}

class AccountingDatesForm (tService: TimeService) extends AccountingDatesFormT {
  override lazy val timeService: TimeService = tService
  override val now: LocalDate   = SystemDate.getSystemDate
}

trait AccountingDatesFormT extends EmptyStringValidator with SCRSValidatorsT {

  val timeService: TimeService

  def form = Form(
    mapping(
      "businessStartDate" -> customErrorTextValidation,
      "businessStartDate-futureDate.year" -> optional(text),
      "businessStartDate-futureDate.month" -> optional(text),
      "businessStartDate-futureDate.day" -> optional(text)
    )(AccountingDatesModel.apply)(AccountingDatesModel.unapply).verifying(StopOnFirstFail(emptyDateConstraint, validateDate, accountingDateValidation))
  )
}


