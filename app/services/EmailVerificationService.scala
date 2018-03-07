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

import audit.events.{EmailVerifiedEvent, EmailVerifiedEventDetail}
import config.{FrontendAuditConnector, FrontendConfig}
import connectors.{CompanyRegistrationConnector, EmailVerificationConnector, KeystoreConnector, SendTemplatedEmailConnector}
import models.Email.GG
import models.auth.AuthDetails
import models.{Email, _}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.auth.core.retrieve.{Email => GGEmail}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.control.NoStackTrace

private[services] class EmailBlockNotFound extends NoStackTrace

object EmailVerificationService extends EmailVerificationService with ServicesConfig {
  val emailConnector = EmailVerificationConnector
  val templatedEmailConnector = SendTemplatedEmailConnector
  val crConnector = CompanyRegistrationConnector
  val returnUrl = FrontendConfig.self
  val keystoreConnector = KeystoreConnector
  val auditConnector = FrontendAuditConnector
  val sendTemplatedEmailURL = getConfString("email.returnToSCRSURL", throw new Exception("email.returnToSCRSURL not found"))
}

trait EmailVerificationService {

  val emailVerificationTemplate = "register_your_company_verification_email"
  val registerYourCompanyEmailTemplate = "register_your_company_welcome_email"
  val returnUrl: String

  val emailConnector: EmailVerificationConnector
  val templatedEmailConnector: SendTemplatedEmailConnector
  val crConnector: CompanyRegistrationConnector
  val keystoreConnector : KeystoreConnector
  val auditConnector : AuditConnector
  val sendTemplatedEmailURL : String

  private[services] def cacheEmail(email: String)(implicit hc: HeaderCarrier): Future[CacheMap] = {
    keystoreConnector.cache("email", email)
  }

  def isVerified(rId: String, oEmail: Option[Email], resend: Option[Boolean], authDetails : AuthDetails)
                (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[(Option[Boolean], Option[String])] = {
    getEmail(rId, oEmail, resend, authDetails) flatMap {
      case Email("", _, _, _, _) => Future.successful((None, None))
      case Email(address, _, _, true, _) => Future.successful((Some(true), Some(address)))
      case Email(address, _, false, _, _) => sendVerificationLink(address, rId, authDetails).map((_, Some(address)))
      case Email(address, _, _, false, _) => verifyEmailAddress(address, rId, authDetails).map((_, Some(address)))
    }
  }

  private[services] def getEmail(regId: String, oEmail: Option[Email], resend: Option[Boolean], authDetails: AuthDetails)
                                (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Email] = {
    (oEmail, resend) match {
      case (Some(email), Some(true)) => Future.successful(email.copy(linkSent = false))
      case (Some(email), _) => Future.successful(email)
      case (None, _) =>
        val email = Email(authDetails.email, "GG", linkSent = false, verified = false, returnLinkEmailSent = false)
        saveEmailBlock(regId, email, authDetails) map { x =>
          email
        }
    }
  }

  private[services] def verifyEmailAddress(address: String, rId: String, authDetails: AuthDetails)
                                          (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
    cacheEmail(address) flatMap { x =>
      emailConnector.checkVerifiedEmail(address) flatMap {
        case true =>
          saveEmailBlock(rId, Email(address, GG, true, verified = true, false), authDetails) map {
            _ => Some(true)
          }
        case _ =>
          saveEmailBlock(rId, Email(address, GG, true, verified = false, false), authDetails) map { e =>
            Some(false)
          }
      }
    }
  }

  private[services] def sendVerificationLink(address: String, rId: String, authDetails: AuthDetails)
                                            (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
    cacheEmail(address) flatMap { cache =>
      emailConnector.requestVerificationEmail(generateEmailRequest(address)) flatMap {
        sent =>
          val verified = !sent // if not sent the it's because the email address was already verified
          saveEmailBlock(rId, Email(address, GG, sent, verified, false), authDetails) map { seb =>
            Some(verified)
          }
      }
    }
  }

  def sendWelcomeEmail(rId: String, emailAddress : String, authDetails: AuthDetails)
                      (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
    fetchEmailBlock(rId) flatMap {
      case Email(_, _, _, _, true) => Future.successful(Some(false))
      case Email(_, _, _, _, false) =>
        templatedEmailConnector.requestTemplatedEmail(generateWelcomeEmailRequest(Seq(emailAddress))) flatMap {
          sent =>
            saveEmailBlock(
              rId,
              Email(emailAddress, "GG", linkSent = true, verified = true, returnLinkEmailSent = true),
              authDetails
            ) map { seb => Some(true) }
        }
    }
  }

    private[services] def generateWelcomeEmailRequest(emailAddress: Seq[String]): SendTemplatedEmailRequest = {
      SendTemplatedEmailRequest(
      to = emailAddress,
      templateId = registerYourCompanyEmailTemplate,
      parameters = Map(
      "returnLink" -> sendTemplatedEmailURL),
      force = true
    )
  }


  private[services] def generateEmailRequest(address: String): EmailVerificationRequest = {
    EmailVerificationRequest(
      email = address,
      templateId = emailVerificationTemplate,
      templateParameters = Map(),
      linkExpiryDuration = "P1D",
      continueUrl = s"$returnUrl/register-your-company/post-sign-in"
    )
  }

  def fetchEmailBlock(regId: String)(implicit hc: HeaderCarrier): Future[Email] = {
    crConnector.retrieveEmail(regId) map {
      case Some(email) => email
      case None => throw new EmailBlockNotFound
    }
  }

  private def saveEmailBlock(regId: String, email: Email, authDetails: AuthDetails)(implicit hc: HeaderCarrier, req: Request[AnyContent]) = {
    crConnector.updateEmail(regId, email) flatMap {
      case oe@Some(Email(address, _, sent, true, _)) =>
        val previouslyVerified = !sent
        emailAuditing(regId, address, previouslyVerified, authDetails).map(_ => oe)
      case oe =>
        Future.successful(oe)
    }
  }

  private def emailAuditing(rId : String, emailAddress : String, previouslyVerified : Boolean, authDetails: AuthDetails)
                           (implicit hc : HeaderCarrier, req: Request[AnyContent]) = {
    for {
      result <- auditConnector.sendExtendedEvent(
        new EmailVerifiedEvent(
          EmailVerifiedEventDetail(
            authDetails.externalId,
            authDetails.authProviderId.providerId,
            rId,
            emailAddress,
            isVerifiedEmailAddress = true,
            previouslyVerified
          )
        )
      )
    } yield result
  }
}
