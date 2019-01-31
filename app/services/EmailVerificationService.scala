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

import audit.events.{EmailVerifiedEvent, EmailVerifiedEventDetail}
import config.{FrontendAuditConnector, FrontendConfig}
import connectors.{CompanyRegistrationConnector, EmailVerificationConnector, KeystoreConnector, SendTemplatedEmailConnector}
import models.Email.GG
import models.auth.AuthDetails
import models.{Email, _}
import play.api.mvc.{AnyContent, Request, Result, Results}
import play.api.mvc.Result._
import uk.gov.hmrc.auth.core.retrieve.{Email => GGEmail}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import utils.SCRSFeatureSwitches

import scala.concurrent.Future
import scala.util.control.NoStackTrace

private[services] class EmailBlockNotFound extends NoStackTrace

sealed trait EmailVerified
case class NoEmail() extends EmailVerified
case class VerifiedEmail() extends EmailVerified
case class NotVerifiedEmail() extends EmailVerified

object EmailVerificationService extends EmailVerificationService with ServicesConfig {
  val emailConnector = EmailVerificationConnector
  val templatedEmailConnector = SendTemplatedEmailConnector
  val crConnector = CompanyRegistrationConnector
  val returnUrl = FrontendConfig.self
  val keystoreConnector = KeystoreConnector
  val auditConnector = FrontendAuditConnector
  val sendTemplatedEmailURL = getConfString("email.returnToSCRSURL", throw new Exception("email.returnToSCRSURL not found"))
  val handOffService = HandOffServiceImpl
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
    saveEmailBlock(regId, email) map { x =>
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
          verifyEmailAddressAndSaveEmailBlockWithFlag(address, rId) flatMap  {
            case Some(true) => cacheReg(VerifiedEmail())
            case _ => cacheReg(NotVerifiedEmail())
        }
        case Some(Email(address, _, linkSent@false, _, _)) => cacheReg(NotVerifiedEmail())
      }
  }


  def verifyEmailAddressAndSaveEmailBlockWithFlag(address: String, rId: String)
                                                                   (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
      emailConnector.checkVerifiedEmail(address) flatMap { emailVerified =>
        saveEmailBlock(rId, Email(address, GG, linkSent = true, verified = emailVerified, returnLinkEmailSent = false)) map {
          _ => Some(emailVerified)
        }
    }
  }

  def sendVerificationLink(address: String, rId: String)
                                            (implicit hc: HeaderCarrier, req: Request[AnyContent]): Future[Option[Boolean]] = {
      emailConnector.requestVerificationEmailReturnVerifiedEmailStatus(generateEmailRequest(address)) flatMap {
        verified =>
          //todo - move the audit: if existing email link sent false and then verified returns true then audit
          saveEmailBlock(rId, Email(address, GG, !verified, verified, false)) map { seb =>
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

  private def saveEmailBlock(regId: String, email: Email)(implicit hc: HeaderCarrier, req: Request[AnyContent]):Future[Option[Email]] = {
    crConnector.updateEmail(regId, email)
    //flatMap {
//      case oe@Some(Email(address, _, linkSent, verified@true, _)) =>
//        val previouslyVerified = !linkSent
//        emailAuditing(regId, address, previouslyVerified, authDetails).map(_ => oe)
//      case oe =>
//        Future.successful(oe)
    //}
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

  private[services] def isUserScp(implicit hc : HeaderCarrier) : Future[Boolean] = {
    //TODO this function will check whether a user is an SCP one when we get the technical detail on how to determine it.
    //TODO for now this will always return false
    Future.successful(false)
  }


}
