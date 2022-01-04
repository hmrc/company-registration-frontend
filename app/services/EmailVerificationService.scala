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

import audit.events.{EmailVerifiedEvent, EmailVerifiedEventDetail}
import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, EmailVerificationConnector, KeystoreConnector, SendTemplatedEmailConnector}
import models.Email.GG
import models.auth.AuthDetails
import models.{Email, _}
import play.api.mvc.{AnyContent, Request, Result, Results}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

private[services] class EmailBlockNotFound extends NoStackTrace

sealed trait EmailVerified
case class NoEmail() extends EmailVerified
case class VerifiedEmail() extends EmailVerified
case class NotVerifiedEmail() extends EmailVerified

class EmailVerificationServiceImpl @Inject()(val emailConnector: EmailVerificationConnector,
                                             val crConnector: CompanyRegistrationConnector,
                                             val keystoreConnector: KeystoreConnector,
                                             val auditConnector: AuditConnector,
                                             val handOffService: HandOffService,
                                             val appConfig: FrontendAppConfig,
                                             val templatedEmailConnector: SendTemplatedEmailConnector) extends EmailVerificationService {
  lazy val returnUrl = appConfig.self
  lazy val sendTemplatedEmailURL = appConfig.servicesConfig.getConfString("email.returnToSCRSURL", throw new Exception("email.returnToSCRSURL not found"))
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
  val handOffService : HandOffService


  private def emailChecks(compRegEmailOpt: Option[Email], rId:String, authDetails: AuthDetails)(implicit hc: HeaderCarrier,req: Request[AnyContent]): Future[Option[Email]] = {
  compRegEmailOpt match {
    case _ if authDetails.email == "" => Future.successful(None)
    case Some(Email(address,_,_,_,_)) if address == "" => Future.successful(None)
    case None if authDetails.email != "" => noEmailBlock(rId,authDetails).map(e =>  Some(e))

    case em@Some(e) => Future.successful(em)
  }
}
  private def noEmailBlock(regId:String, authDetails: AuthDetails)(implicit hc: HeaderCarrier,req: Request[AnyContent]) = {
    val email = Email(authDetails.email, "GG", linkSent = false, verified = false, returnLinkEmailSent = false)
    saveEmailBlock(regId, email,authDetails.authProviderId.providerId, authDetails.externalId) map { x =>
      email
    }
  }

  def checkEmailStatus(rId: String, oEmail: Option[Email], authDetails : AuthDetails)
  (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[EmailVerified] = {

    def cacheReg(emv: EmailVerified) = handOffService.cacheRegistrationID(rId).map(_ => emv)

    emailChecks(oEmail, rId, authDetails) flatMap {
        case None => Future.successful(NoEmail())

        case Some(Email(address, _, _, scrsVerified@true, _)) => cacheReg(VerifiedEmail())

        case Some(Email(address, _, linkSent@true, _, _)) =>
          verifyEmailAddressAndSaveEmailBlockWithFlag(address, rId, authDetails.authProviderId.providerId,authDetails.externalId) flatMap  {
            case Some(true) => cacheReg(VerifiedEmail())
            case _ => cacheReg(NotVerifiedEmail())
        }
        case Some(Email(address, _, linkSent@false, _, _)) => cacheReg(NotVerifiedEmail())
      }
  }


  def verifyEmailAddressAndSaveEmailBlockWithFlag(address: String, rId: String, authProviderId: String, externalId: String)
                                                                   (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
      emailConnector.checkVerifiedEmail(address) flatMap { emailVerified =>
        saveEmailBlock(rId, Email(address, GG, linkSent = true, verified = emailVerified, returnLinkEmailSent = false), authProviderId, externalId) map {
          _ => Some(emailVerified)
        }
    }
  }

  def sendVerificationLink(address: String, rId: String, authProviderId: String, externalId:String)
                                            (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
      emailConnector.requestVerificationEmailReturnVerifiedEmailStatus(generateEmailRequest(address)) flatMap {
        verified =>
          saveEmailBlock(rId, Email(address, GG, !verified, verified, false),authProviderId,externalId) map { seb =>
            Some(verified)
          }
    }
  }

  private[services] def generateEmailRequest(address: String): EmailVerificationRequest = {
    EmailVerificationRequest(
      email = address,
      templateId = emailVerificationTemplate,
      templateParameters = Map(),
      linkExpiryDuration = "P3D",
      continueUrl = s"$returnUrl/register-your-company/post-sign-in"
    )
  }
  def fetchEmailBlock(regId: String)(implicit hc: HeaderCarrier):Future[Option[Email]] ={
    crConnector.retrieveEmail(regId)
  }

  def emailVerifiedStatusInSCRS(rId:String, f: () => Future[Result])(implicit hc: HeaderCarrier): Future[Result] = {

    fetchEmailBlock(rId).flatMap {
      e =>
        if(e.exists(_.verified) || e.isEmpty) {
          Future.successful(Results.Redirect(controllers.reg.routes.SignInOutController.postSignIn(None)))
        } else {
          f()
        }
    }
  }

  def saveEmailBlock(regId: String, email: Email, authProviderId: String, externalId: String)(implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Email]] = {
    crConnector.updateEmail(regId, email)
      .flatMap { emailFromUpdate =>
        if(email.verified) {
          emailAuditing(regId, email.address, authProviderId, externalId).map(_ => emailFromUpdate)
        } else {
          Future.successful(emailFromUpdate)
        }
      }
  }

  private def emailAuditing(rId : String, emailAddress : String, authProviderId: String, externalId: String)
                           (implicit hc : HeaderCarrier, req: Request[AnyContent]) = {
    for {
      result <- auditConnector.sendExtendedEvent(
        new EmailVerifiedEvent(
          EmailVerifiedEventDetail(
            externalId,
            authProviderId,
            rId,
            emailAddress
          )
        )
      )
    } yield result
  }
}