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

package fixtures

import models._
import models.AccountingDatesModel.{FUTURE_DATE, NOT_PLANNING_TO_YET, WHEN_REGISTERED}

trait AccountingDetailsFixture {

	lazy val validAccountingDetailsResponse = AccountingDetails(
    FUTURE_DATE,
		Some("2016-08-03"),
		Links(Some("/business-registration/business-tax-registartion/12345"))
	)
	lazy val validAccountingDetailsResponse2 = AccountingDetails(
    FUTURE_DATE,
		Some("1980-12-03"),
		Links(Some("/business-registration/business-tax-registartion/12345"))
	)

	lazy val validAccountingDetailsModel = AccountingDatesModel(
    FUTURE_DATE,
		Some("12"),
		Some("12"),
		Some("1980")
	)

	lazy val accountingDetailsRequest = AccountingDetailsRequest(FUTURE_DATE, Some("2017-12-10"))

	lazy val validAccountingResponse = AccountingDetailsSuccessResponse(AccountingDetails(FUTURE_DATE, Some("2020-06-10"), Links(Some(""))))

}
