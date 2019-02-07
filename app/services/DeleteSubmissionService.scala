/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.CompanyRegistrationConnector
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DeleteSubmissionServiceImpl @Inject()(val crConnector: CompanyRegistrationConnector) extends DeleteSubmissionService

trait DeleteSubmissionService {
  val crConnector: CompanyRegistrationConnector

  def deleteSubmission(regId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    crConnector.deleteRegistrationSubmission(regId) map { res => true} recover {
      case ex: Exception =>
        Logger.error(s"[DeleteSubmissionService] [deleteSubmission] Submission was not deleted.  Message received was ${ex.getMessage}")
        false
    }  }
}