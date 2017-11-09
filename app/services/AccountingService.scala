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

package services

import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import models.{AccountingDetailsRequest, AccountingDatesModel, AccountingDetailsResponse, AccountingDetailsSuccessResponse}
import utils.SCRSExceptions
import models.AccountingDatesModel.{FUTURE_DATE, WHEN_REGISTERED, NOT_PLANNING_TO_YET}

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http.HeaderCarrier

object AccountingService extends AccountingService {
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
}

trait AccountingService extends CommonService with SCRSExceptions {

  val companyRegistrationConnector: CompanyRegistrationConnector

  def fetchAccountingDetails(implicit hc: HeaderCarrier): Future[AccountingDatesModel] = {
    for {
      registrationID <- fetchRegistrationID
      accountingDetails <- companyRegistrationConnector.retrieveAccountingDetails(registrationID)
    } yield {
      accountingDetails match {
        case AccountingDetailsSuccessResponse(details) => details.copy(details.accountingDateStatus match {
          case WHEN_REGISTERED => "whenRegistered"
          case FUTURE_DATE => "futureDate"
          case NOT_PLANNING_TO_YET => "notPlanningToYet"
          case _ => ""
        })
        case _ => AccountingDatesModel("", None, None, None)
      }
    }
  }

  def updateAccountingDetails(accountingDetails: AccountingDatesModel)(implicit hc: HeaderCarrier): Future[AccountingDetailsResponse] = {
    for {
      registrationID <- fetchRegistrationID
      accDetailsResp <- companyRegistrationConnector.updateAccountingDetails(registrationID, AccountingDetailsRequest.toRequest(accountingDetails))
    } yield {
      accDetailsResp
    }
  }
}
