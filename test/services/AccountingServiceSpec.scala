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

package services

import fixtures.AccountingDetailsFixture
import helpers.SCRSSpec
import models._

class AccountingServiceSpec extends SCRSSpec with AccountingDetailsFixture {

  class Setup {
    val service = new AccountingService {
      val companyRegistrationConnector = mockCompanyRegistrationConnector
      val keystoreConnector = mockKeystoreConnector
    }
  }

  val url = "http://test.url"

  "fetchAccountingDetails" should {
    "return an AccountingDatesModel for when registered" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("12345"))
        CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsSuccessResponse(AccountingDetails("WHEN_REGISTERED", Some("1-2-3"), Links(Some("")))))
        await(service.fetchAccountingDetails) shouldBe AccountingDatesModel("whenRegistered", Some("1"), Some("2"), Some("3"))
      }
    "return an AccountingDatesModel for future date" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("12345"))
        CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsSuccessResponse(AccountingDetails("FUTURE_DATE", Some("1-2-3"), Links(Some("")))))
        await(service.fetchAccountingDetails) shouldBe AccountingDatesModel("futureDate", Some("1"), Some("2"), Some("3"))
      }
    "return an AccountingDatesModel for not planning to yet" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsSuccessResponse(AccountingDetails("NOT_PLANNING_TO_YET", Some("1-2-3"), Links(Some("")))))
      await(service.fetchAccountingDetails) shouldBe AccountingDatesModel("notPlanningToYet", Some("1"), Some("2"), Some("3"))
    }
    "return an AccountingDatesModel for undefined" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsSuccessResponse(AccountingDetails("", Some("1-2-3"), Links(Some("")))))
      await(service.fetchAccountingDetails) shouldBe AccountingDatesModel("", Some("1"), Some("2"), Some("3"))
    }
    "return an empty AccountingDatesModel when there isn't already a model in MongoDB" in new Setup {
        mockKeystoreFetchAndGet("registrationID", Some("12345"))
        CTRegistrationConnectorMocks.retrieveAccountingDetails(AccountingDetailsNotFoundResponse)
        await(service.fetchAccountingDetails) shouldBe AccountingDatesModel("", None, None, None)
      }
    }

  "updateAccountingDetails" should {
    "return an AccountingDatesModel" in new Setup {
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      CTRegistrationConnectorMocks.updateAccountingDetails(AccountingDetailsSuccessResponse(AccountingDetails("test", Some("1-2-3"), Links(Some("other")))))
      await(service.updateAccountingDetails(
        AccountingDetails("test2", Some("3-2-1"), Links(Some("other"))))
      ) shouldBe AccountingDetailsSuccessResponse(AccountingDetails("test", Some("1-2-3"), Links(Some("other"), None)))
    }
  }
}