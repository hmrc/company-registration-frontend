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


import java.time.Instant
import java.util.UUID

import audit.events.{AuditEventConstants, ContactDetailsAuditEventDetail, EmailMismatchEventDetail, EmailVerifiedEventDetail, QuestionnaireAuditEvent}
import javax.inject.{Inject, Singleton}
import models.{CompanyContactDetails, QuestionnaireModel}
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuditServiceImpl @Inject()(val auditConnector: AuditConnector) extends AuditService {
}


trait AuditService {
  val auditConnector: AuditConnector

  private[services] def now() = Instant.now()

  private[services] def eventId() = UUID.randomUUID().toString

  def sendEvent[T](auditType: String, detail: T, transactionName: Option[String] = None)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext, fmt: Writes[T]): Future[AuditResult] = {

    val event = ExtendedDataEvent(
      auditSource = auditConnector.auditingConfig.auditSource,
      auditType = auditType,
      eventId = eventId(),
      tags = hc.toAuditTags(
        transactionName = transactionName.getOrElse(auditType),
        path = hc.otherHeaders.collectFirst { case (AuditEventConstants.PATH, value) => value }.getOrElse("-")
      ),
      detail = Json.toJson(detail),
      generatedAt = now()
    )

    auditConnector.sendExtendedEvent(event)
  }

  def auditChangeInContactDetails(externalID: String, authProviderId: String, rID: String,
                                  originalEmail: String,
                                  amendedContactDetails: CompanyContactDetails,
                                 )
                                 (implicit hc: HeaderCarrier, req: Request[AnyContent], ec: ExecutionContext): Future[AuditResult] =
    sendEvent(
      auditType = "changeInContactDetails",
      detail = ContactDetailsAuditEventDetail(externalID, authProviderId, rID, originalEmail, amendedContactDetails)
    )


  def emailMismatchEventDetail(externalUserId: String,
                               authProviderId: String,
                               journeyId: String)
                              (implicit hc: HeaderCarrier, req: Request[AnyContent], ec: ExecutionContext): Future[AuditResult] =

    sendEvent(
      auditType = "emailMismatch",
      detail = EmailMismatchEventDetail(externalUserId, authProviderId, journeyId)
    )


  def emailVerifiedEventDetail(externalUserId: String, authProviderId: String,
                               journeyId: String,
                               emailAddress: String, isVerifiedEmailAddress: Boolean = true,
                               previouslyVerified: Boolean = true)
                              (implicit hc: HeaderCarrier, req: Request[AnyContent], ec: ExecutionContext): Future[AuditResult] =

    sendEvent(
      auditType = "emailVerified",
      detail = EmailVerifiedEventDetail(externalUserId, authProviderId, journeyId, emailAddress, isVerifiedEmailAddress, previouslyVerified)
    )

  def questionnaireAuditEvent(questionnaireModel: QuestionnaireModel)
                             (implicit hc: HeaderCarrier, req: Request[AnyContent], ec: ExecutionContext): Future[AuditResult] =

    sendEvent(
      auditType = "Questionnaire",
      detail = questionnaireModel
    )

}

