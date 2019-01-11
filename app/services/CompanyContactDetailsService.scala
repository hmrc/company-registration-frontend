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

import audit.events.{ContactDetailsAuditEvent, ContactDetailsAuditEventDetail}
import config.{FrontendAuditConnector, FrontendAuthConnector}
import connectors._
import models._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import utils.SCRSExceptions

import scala.concurrent.Future

object CompanyContactDetailsService extends CompanyContactDetailsService {
  val businessRegConnector = BusinessRegistrationConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
  val authConnector = FrontendAuthConnector
  val auditConnector = FrontendAuditConnector
  val platformAnalyticsConnector = PlatformAnalyticsConnector
}

trait CompanyContactDetailsService extends CommonService with SCRSExceptions {
  val businessRegConnector: BusinessRegistrationConnector
  val companyRegistrationConnector: CompanyRegistrationConnector
  val auditConnector: AuditConnector
  val platformAnalyticsConnector: PlatformAnalyticsConnector

  def fetchContactDetails(emailFromAuth : String)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsApi] = {
    fetchRegistrationID flatMap {
      companyRegistrationConnector.retrieveContactDetails
    } flatMap {
      case CompanyContactDetailsSuccessResponse(details) => Future.successful(CompanyContactDetails.toApiModel(details))
      case _ =>
        if (isEmailValid(emailFromAuth)) {
          Future.successful(CompanyContactDetailsApi(Some(emailFromAuth), None, None))
        } else {
          platformAnalyticsConnector.sendEvents(GAEvents.invalidDESEmailFromUserDetails).map { _ =>
            CompanyContactDetailsApi(None, None, None)
          }
        }
    }
  }

  def updatePrePopContactDetails(registrationId: String, contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier): Future[Boolean] = {
    businessRegConnector.updatePrePopContactDetails(registrationId, contactDetails)
  }

  private[services] def isEmailValid(email: String): Boolean = {
    val emailValidation = """^[A-Za-z0-9\-_.@]{1,70}$"""
    email.matches(emailValidation)
  }


  def updateContactDetails(contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    for {
      registrationID <- fetchRegistrationID
      contactDetails <- companyRegistrationConnector.updateContactDetails(registrationID, contactDetails)
    } yield {
      contactDetails
    }
  }

  def checkIfAmendedDetails(email: String, cred : Credentials, externalID: String, userContactDetails: CompanyContactDetails)
                           (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Boolean] = {
    if (isContactDetailsAmended(userContactDetails.contactEmail, email)) {
      for {
        regId <- fetchRegistrationID
        _ = auditChangeInContactDetails(externalID, cred.providerId, regId, email, userContactDetails)
      } yield true
    } else {
      Future.successful(false)
    }
  }

  private[services] def isContactDetailsAmended(userSubmittedEmail: Option[String], ggEmail: String): Boolean = {
    !userSubmittedEmail.contains(ggEmail)
  }

  private[services] def auditChangeInContactDetails(externalID: String, authProviderId: String, rID: String,
                                                    originalEmail: String,
                                                    amendedContactDetails: CompanyContactDetails)
                                                   (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[AuditResult] = {

    val event = new ContactDetailsAuditEvent(ContactDetailsAuditEventDetail(externalID, authProviderId, rID, originalEmail, amendedContactDetails))
    auditConnector.sendExtendedEvent(event)
  }
}
