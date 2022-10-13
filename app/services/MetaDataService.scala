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

import javax.inject.Inject

import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse, KeystoreConnector}
import models.{AboutYouChoice, AboutYouChoiceForm, BusinessRegistration}
import utils.Logging
import uk.gov.hmrc.http.HeaderCarrier
import utils.SCRSExceptions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MetaDataServiceImpl @Inject()(val businessRegConnector: BusinessRegistrationConnector,
                                    val keystoreConnector: KeystoreConnector) extends MetaDataService

trait MetaDataService extends CommonService with SCRSExceptions with Logging {

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

  def getApplicantData(regId: String)(implicit hc : HeaderCarrier) : Future[AboutYouChoice] = {
    businessRegConnector.retrieveMetadata(regId) map {
      case BusinessRegistrationSuccessResponse(resp) => AboutYouChoice(resp.completionCapacity.getOrElse(
        throw new RuntimeException(s"Completion capacity missing at Summary for regId: $regId"))
      )
      case unknown => {
        logger.warn(s"[getApplicantData] Unexpected result, unable to get BR doc : ${unknown}")
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
