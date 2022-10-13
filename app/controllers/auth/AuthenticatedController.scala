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

package controllers.auth

import config.AppConfig
import models.auth.{AuthDetails, BasicCompanyAuthDetails}
import utils.Logging
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{credentials, emailVerified, name, _}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, _}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import scala.concurrent.{ExecutionContext, Future}

trait AuthenticatedController extends FrontendBaseController with AuthorisedFunctions with Logging {
  val appConfig: AppConfig
  val baseFunction: AuthorisedFunction = authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L50)
  implicit val ec: ExecutionContext

  def ctAuthorised(body: => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction(body) recover authErrorHandling()
  }

  def ctAuthorisedHandoff(hoID: String, payload: String)(body: => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction(body) recover authErrorHandling(Some(hoID), Some(payload))
  }

  def ctAuthorisedOptStr(retrieval: Retrieval[Option[String]])(body: => (String) => Future[Result])
                        (implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(retrieval) {
      case Some(ret) => body(ret)
      case _ => Future.failed(InternalError(s"[AuthConnector] ctAuthorisedOptStr returned None when expecting Some of ${retrieval.propertyNames}"))
    } recover authErrorHandling()
  }

  def ctAuthorisedExternalIDIncomplete(body: => (Option[String]) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(externalId)(body(_)) recover authErrorHandlingIncomplete
  }

  def ctAuthorisedBasicCompanyDetails(body: => (BasicCompanyAuthDetails) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(name and email and externalId) {
      case Some(nm) ~ Some(em) ~ Some(ei) => body(BasicCompanyAuthDetails(nm.name.get, em, ei))
      case nm ~ None ~ Some(ei) => {
        logger.info("[ctAuthorisedBasicCompanyDetails] user does not have email on gg record (call from auth)")
        Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.createShow))
      }
      case _ => Future.failed(InternalError("ctAuthorisedBasicCompanyDetails auth response was incorrect to what we expected when we were extracting Retrievals"))
    } recover authErrorHandling()
  }

  def ctAuthorisedCompanyContact(body: => (String) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(name and email) {
      case nm ~ Some(em) => body(em)
      case nm ~ None => {
        logger.info("[ctAuthorisedCompanyContact] user does not have email on gg record (call from auth)")
        Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.createShow))
      }
      case _ => Future.failed(InternalError("ctAuthorisedCompanyContact auth response was incorrect to what we expected when we were extracting Retrievals"))
    } recover authErrorHandling()
  }


  def ctAuthorisedEmailCredsExtId(body: => (String, String, String) => Future[Result])
                                 (implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(name and email and credentials and externalId) {
      case nm ~ Some(em) ~ Some(cr) ~ Some(ei) => body(em, cr.providerId, ei)
      case nm ~ None ~ cr ~ Some(ei) => {
        logger.info("[ctAuthorisedEmailCredsExtId] user does not have email on gg record (call from auth)")
        Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.createShow))
      }
      case _ => Future.failed(InternalError("ctAuthorisedEmailCredsExtId auth response was incorrect to what we expected when we were extracting Retrievals"))
    } recover authErrorHandling()
  }

  def ctAuthorisedCredID(body: => String => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(credentials) {
      case Some(cr) => body(cr.providerId)
      case _ => Future.failed(InternalError("ctAuthorisedCredID auth response was incorrect to what we expected when we were extracting Retrievals"))
    } recover authErrorHandling()
  }


  def ctAuthorisedPostSignIn(body: => (AuthDetails) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(affinityGroup and allEnrolments and email and internalId and credentials) {
      case Some(ag) ~ ae ~ Some(em) ~ Some(ii) ~ Some(api) => body(AuthDetails(ag, ae, em, ii, api.providerId))
      case Some(ag) ~ ae ~ None ~ Some(ii) ~ api => {
        logger.info("[ctAuthorisedPostSignIn] user does not have email on gg record (call from auth)")
        Future.successful(Redirect(controllers.verification.routes.EmailVerificationController.createShow))
      }
      case _ => Future.failed(InternalError("ctAuthorisedPostSignIn auth response was incorrect to what we expected when we were extracting Retrievals"))
    } recover authErrorHandling()
  }


  lazy val origin: String = appConfig.servicesConfig.getString("appName")

  def loginParams(hoID: Option[String], payload: Option[String]) = Map(
    "continue_url" -> Seq(appConfig.continueURL(hoID, payload)),
    "origin" -> Seq(origin)
  )


  def authErrorHandlingIncomplete(implicit request: Request[AnyContent]): PartialFunction[Throwable, Result] = {
    case e: NoActiveSession => {
      logger.info(s"[authErrorHandlingIncomplete] Reason for NoActiveSession: ${e.reason}")
      Redirect(controllers.reg.routes.IncompleteRegistrationController.show)
    }
    case InternalError(e) =>
      logger.warn(s"[authErrorHandlingIncomplete] Something went wrong with a call to Auth with exception: ${e}")
      InternalServerError
    case e: AuthorisationException =>
      logger.error(s"[authErrorHandlingIncomplete] auth returned $e and redirected user to 'incorrect-account-type' page")
      Redirect(controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow)
  }

  def authErrorHandling(hoID: Option[String] = None, payload: Option[String] = None)
                       (implicit request: Request[AnyContent]): PartialFunction[Throwable, Result] = {
    case e: NoActiveSession => {
      logger.info(s"[authErrorHandling] Reason for NoActiveSession: ${e.reason} HO was ${hoID.fold("None")(ho => ho)} Payload was ${payload.fold("None")(pl => pl)}")
      Redirect(appConfig.loginURL, loginParams(hoID, payload))
    }
    case InternalError(e) =>
      logger.warn(s"[authErrorHandling] Something went wrong with a call to Auth with exception: ${e}")
      InternalServerError
    case e: AuthorisationException =>
      logger.error(s"[authErrorHandling] auth returned $e and redirected user to 'incorrect-account-type' page")
      Redirect(controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow)
  }

  def scpVerifiedEmail(implicit request: Request[AnyContent]): Future[Boolean] = {
    baseFunction.retrieve(emailVerified) {
      case Some(em) => Future.successful(em)
      case _ => Future.successful(false)
    } recover {
      case _ => false
    }
  }
}