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

import audit.events.QuestionnaireAuditEvent
import javax.inject.Inject
import models.QuestionnaireModel
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class QuestionnaireServiceImpl @Inject()(val auditConnector: AuditConnector) extends QuestionnaireService

trait QuestionnaireService {

  val auditConnector: AuditConnector

  def sendAuditEventOnSuccessfulSubmission(questionnaire: QuestionnaireModel)(implicit hc: HeaderCarrier, request:Request[AnyContent]): Future[AuditResult] = {
    val event = new QuestionnaireAuditEvent(questionnaire)
    auditConnector.sendExtendedEvent(event)
  }
}