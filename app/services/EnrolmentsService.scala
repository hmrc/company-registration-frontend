/*
 * Copyright 2018 HM Revenue & Customs
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

import config.{FrontendAppConfig, FrontendAuthConnector, WSHttp}
import models.Enrolments
import play.api.libs.json.JsArray
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector

import scala.concurrent.Future
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.http.{CoreGet, HeaderCarrier, HttpGet, HttpReads}

object EnrolmentsService extends EnrolmentsService {
  val authConnector = FrontendAuthConnector
  val http = WSHttp
}

trait EnrolmentsService extends {

  val authConnector : AuthConnector
  val http : HttpGet with CoreGet

  def hasBannedRegimes(user : AuthContext)(implicit hc : HeaderCarrier, reads : HttpReads[List[Enrolments]]) : Future[Boolean] = {
    authConnector.getEnrolments[JsArray](user) map {
      jsArr =>
        val enrolments = jsArr.value.map(_.as[Enrolments]).map(_.enrolment)
        hasAnyEnrolments(enrolments, FrontendAppConfig.restrictedEnrolments)
    }
  }

  private[services] def hasAnyEnrolments(currentEnrolments: Seq[String], checkEnrolments: Seq[String]) = {
    currentEnrolments.exists(e => checkEnrolments.contains(e))
  }
}
