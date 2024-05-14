/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.events._
import connectors._
import javax.inject.Inject
import models._
import utils.Logging
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import utils.SCRSExceptions

import scala.concurrent.{ExecutionContext, Future}

class CompanyContactDetailsServiceImpl @Inject()(val businessRegConnector: BusinessRegistrationConnector,
                                                 val companyRegistrationConnector: CompanyRegistrationConnector,
                                                 val keystoreConnector: KeystoreConnector,
                                                 val authConnector: PlayAuthConnector,
                                                 val auditConnector: AuditConnector)(implicit val ec: ExecutionContext) extends CompanyContactDetailsService

trait CompanyContactDetailsService extends CommonService with AuditService with SCRSExceptions with Logging {
  val businessRegConnector: BusinessRegistrationConnector
  val companyRegistrationConnector: CompanyRegistrationConnector
  val auditConnector: AuditConnector

  def fetchContactDetails(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyContactDetailsApi] = {


    val getVerifiedEmailAndContactDetails: Future[(String, CompanyContactDetailsResponse)] = for {
      regId <- fetchRegistrationID
      verEmail <- companyRegistrationConnector.retrieveEmail(regId) map {
        e =>
          e.fold {
            logger.warn("[CompanyContactDetails] No Email in verified block")
            throw new NoSuchElementException("Verified Email not found")
          } {
            _.address
          }
      }
      contactDetails <- companyRegistrationConnector.retrieveContactDetails(regId)
    } yield {
      (verEmail, contactDetails)
    }


    getVerifiedEmailAndContactDetails.flatMap {
      case (_, CompanyContactDetailsSuccessResponse(details)) => Future.successful(CompanyContactDetails.toApiModel(details))
      case (verifiedEmail, _) =>
        if (isEmailValid(verifiedEmail)) {
          Future.successful(CompanyContactDetailsApi(Some(verifiedEmail), None, None))
        } else {
          Future.successful(CompanyContactDetailsApi(None, None, None))
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


  def updateContactDetails(contactDetails: CompanyContactDetailsApi)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyContactDetailsResponse] = {
    for {
      registrationID <- fetchRegistrationID
      contactDetails <- companyRegistrationConnector.updateContactDetails(registrationID, contactDetails)
    } yield {
      contactDetails
    }
  }

  def checkIfAmendedDetails(email: String, cred: String, externalID: String, userContactDetails: CompanyContactDetails)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext, req: Request[AnyContent]): Future[Boolean] = {
    if (isContactDetailsAmended(userContactDetails.contactEmail, email)) {
      for {
        regId <- fetchRegistrationID
        _ = auditChangeInContactDetails(externalID, cred, regId, email, userContactDetails)
      } yield true
    } else {
      Future.successful(false)
    }
  }

  private[services] def isContactDetailsAmended(userSubmittedEmail: Option[String], ggEmail: String): Boolean = {
    !userSubmittedEmail.contains(ggEmail)
  }




}
