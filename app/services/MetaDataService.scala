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

import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse, KeystoreConnector}
import models.{AboutYouChoice, AboutYouChoiceForm, BusinessRegistration}
import play.api.Logger
import utils.SCRSExceptions

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http.HeaderCarrier

object MetaDataService extends MetaDataService {
  val businessRegConnector = BusinessRegistrationConnector
  val keystoreConnector = KeystoreConnector
}

trait MetaDataService extends CommonService with SCRSExceptions {

  val businessRegConnector : BusinessRegistrationConnector

  def updateCompletionCapacity(completionCapacity : AboutYouChoiceForm)(implicit hc : HeaderCarrier) : Future[BusinessRegistration] = {
    for {
      regID <- fetchRegistrationID
      updateCC <- businessRegConnector.retrieveAndUpdateCompletionCapacity(regID, getItemToSave(completionCapacity))
    } yield {
      updateCC
    }
  }

  def getItemToSave(completionCapacity : AboutYouChoiceForm) : String = {
    completionCapacity.completionCapacity match {
      case "director" | "agent" | "company secretary"  => completionCapacity.completionCapacity
      case _ => completionCapacity.completionCapacityOther
    }
  }

  def getApplicantData(implicit hc : HeaderCarrier) : Future[AboutYouChoice] = {
    businessRegConnector.retrieveMetadata map {
      case BusinessRegistrationSuccessResponse(resp) => AboutYouChoice(resp.completionCapacity.fold("")(cc => cc))//todo double check empty cc
      case unknown => {
        Logger.warn(s"[MetaDataService][getApplicantData] Unexpected result, unable to get BR doc : ${unknown}")
        throw new RuntimeException("Missing BR document for signed in user")
      }
    }
  }

  def updateApplicantDataEndpoint(applicantData : AboutYouChoice)(implicit hc : HeaderCarrier) : Future[BusinessRegistration] = {
    for {
      regId <- fetchRegistrationID
      updateCC <- businessRegConnector.retrieveAndUpdateCompletionCapacity(regId, applicantData.completionCapacity)
    } yield {
      updateCC
    }
  }
}
