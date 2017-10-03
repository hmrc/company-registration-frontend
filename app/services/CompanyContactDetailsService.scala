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

import audit.events.{ContactDetailsAuditEventDetail, ContactDetailsAuditEvent}
import config.{FrontendAuditConnector, FrontendAuthConnector}
import connectors._
import models._
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.play.audit.http.connector.{AuditResult, AuditConnector}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier
import utils.SCRSExceptions

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

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
  val authConnector: AuthConnector
  val auditConnector: AuditConnector
  val platformAnalyticsConnector: PlatformAnalyticsConnector

  def fetchContactDetails(implicit user: AuthContext, hc: HeaderCarrier): Future[CompanyContactViewModel] = {
    fetchRegistrationID flatMap {
      companyRegistrationConnector.retrieveContactDetails
    } flatMap {
      case CompanyContactDetailsSuccessResponse(details) => Future.successful(CompanyContactDetails.toViewModel(details))
      case _ => authConnector.getUserDetails[UserDetailsModel](user).flatMap{ userDetails =>
        isEmailValid(userDetails.email) match {
          case true => Future.successful(CompanyContactViewModel(s"${userDetails.name} ${userDetails.lastName.getOrElse("")}", Some(userDetails.email), None, None))
          case false =>
            platformAnalyticsConnector.sendEvents(GAEvents.invalidDESEmailFromUserDetails).map { _ =>
              CompanyContactViewModel(s"${userDetails.name} ${userDetails.lastName.getOrElse("")}", None, None, None)
            }
        }
      }
    }
  }

  def updatePrePopContactDetails(registrationId: String, contactDetails: CompanyContactDetailsMongo)(implicit hc: HeaderCarrier): Future[Boolean] = {
    businessRegConnector.updatePrePopContactDetails(registrationId, contactDetails)
  }

  private[services] def isEmailValid(email: String): Boolean = {
    val emailValidation = """^[A-Za-z0-9\-_.@]{1,70}$"""
    email.matches(emailValidation)
  }


  def updateContactDetails(contactDetails: CompanyContactViewModel)(implicit hc: HeaderCarrier): Future[CompanyContactDetailsResponse] = {
    for {
      registrationID <- fetchRegistrationID
      contactDetails <- companyRegistrationConnector.updateContactDetails(registrationID, contactDetails)
    } yield {
      contactDetails
    }
  }

  def checkIfAmendedDetails(userContactDetails: CompanyContactDetails)(implicit hc: HeaderCarrier, user: AuthContext, req: Request[AnyContent]): Future[Boolean] = {
    authConnector.getUserDetails[UserDetailsModel](user) flatMap {
      userDetails =>
        val originalContactDetails = buildContactDetailsFromUserDetails(userDetails)
        isContactDetailsAmended(userContactDetails, originalContactDetails) match {
          case true =>
            for {
              userIds <- authConnector.getIds[UserIDs](user)
              regId   <- fetchRegistrationID
              _        = auditChangeInContactDetails(userIds.externalId, userDetails.authProviderId, regId, originalContactDetails, userContactDetails)
            } yield true
          case false => Future.successful(false)
        }
    }
  }

  private[services] def buildContactDetailsFromUserDetails(details: UserDetailsModel): CompanyContactDetails = {

    def joinNamesFromUserDetails(name: String, lastName: Option[String]): Name = {
      val lName = lastName.fold("")(n => n)
      val nameList = details.name.split(" ").:+(lName).filter(_ != "").toList
      nameList.size match {
        case 1 => Name(nameList.head, None, None)
        case 2 => Name(nameList.head, None, Some(nameList.reverse.head))
        case s if s > 2 => Name(nameList.head, Some(nameList.drop(1).dropRight(1).mkString(" ")), Some(nameList.reverse.head))
      }
    }

    val name = joinNamesFromUserDetails(details.name, details.lastName)

    CompanyContactDetails.apply(
      Some(name.firstName),
      name.middleName,
      name.surname,
      None,
      None,
      Some(details.email),
      Links(None)
    )
  }

  private[services] def isContactDetailsAmended(details: CompanyContactDetails, ggDetails: CompanyContactDetails): Boolean = {
    !CompanyContactDetails.isEqual(details, ggDetails)
  }

  private[services] def auditChangeInContactDetails(externalID: String, authProviderId: String, rID: String,
                                                    originalContactDetails: CompanyContactDetails,
                                                    amendedContactDetails: CompanyContactDetails)
                                                   (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[AuditResult] = {

    val event = new ContactDetailsAuditEvent(ContactDetailsAuditEventDetail(externalID, authProviderId, rID, originalContactDetails, amendedContactDetails))
    auditConnector.sendEvent(event)
  }
}
