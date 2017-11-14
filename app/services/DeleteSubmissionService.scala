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

import connectors.CompanyRegistrationConnector
import play.api.Logger

import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier


object DeleteSubmissionService extends DeleteSubmissionService {
  val crConnector = CompanyRegistrationConnector
}

trait DeleteSubmissionService {
  val crConnector: CompanyRegistrationConnector

  def deleteSubmission(regId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    crConnector.deleteRegistrationSubmission(regId) map { res => true} recover {
      case ex: Exception =>
        Logger.error(s"[DeleteSubmissionService] [deleteSubmission] Submission was not deleted.  Message received was ${ex.getMessage}")
        false
    }  }
}
