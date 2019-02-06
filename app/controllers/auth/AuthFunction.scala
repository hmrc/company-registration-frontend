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

package controllers.auth

import config.FrontendAppConfig
import models.auth.{AuthDetails, BasicCompanyAuthDetails}
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, _}
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

trait AuthFunction extends FrontendController with AuthorisedFunctions {
  val appConfig: FrontendAppConfig
  val baseFunction: AuthorisedFunction = authorised(AuthProviders(GovernmentGateway) and ConfidenceLevel.L50)

  def ctAuthorised(body: => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction (body) recover authErrorHandling()
  }

  def ctAuthorisedHandoff(hoID : String, payload : String)(body: => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction (body) recover authErrorHandling(Some(hoID), Some(payload))
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
      case nm ~ Some(em) ~ Some(ei) => body(BasicCompanyAuthDetails(nm.name.get, em, ei))
    } recover authErrorHandling()
  }

  def ctAuthorisedCompanyContact(body: => (String) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(name and email) {
      case nm ~ Some(em) => body(em)
    } recover authErrorHandling()
  }

  def ctAuthorisedCompanyContactAmend(body: => (String, Credentials, String) => Future[Result])
                                     (implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(name and email and credentials and externalId) {
      case nm ~ Some(em) ~ cr ~ Some(ei) => body(em, cr, ei)
    } recover authErrorHandling()
  }

  def ctAuthorisedCredID(body: => String => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(credentials)(cr => body(cr.providerId)) recover authErrorHandling()
  }

  def ctAuthorisedPostSignIn(body: => (AuthDetails) => Future[Result])(implicit request: Request[AnyContent]): Future[Result] = {
    baseFunction.retrieve(affinityGroup and allEnrolments and email and internalId and credentials) {
      case Some(ag) ~ ae ~ Some(em) ~ Some(ii) ~ api => body(AuthDetails(ag, ae, em, ii, api))
    } recover authErrorHandling()
  }


  lazy val origin: String = appConfig.getString("appName")
  def loginParams(hoID : Option[String], payload : Option[String]) = Map(
    "continue" -> Seq(appConfig.continueURL(hoID, payload)),
    "origin" -> Seq(origin)
  )


  def authErrorHandlingIncomplete(implicit request: Request[AnyContent]) : PartialFunction[Throwable, Result] = {
    case _: NoActiveSession         => Redirect(controllers.reg.routes.IncompleteRegistrationController.show())
    case InternalError(e)           =>
      Logger.warn(s"Something went wrong with a call to Auth with exception: ${e}")
      InternalServerError
    case e: AuthorisationException  =>
      Logger.error(s"auth returned $e and redirected user to 'incorrect-account-type' page")
      Redirect(controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow())
  }

  def authErrorHandling(hoID : Option[String] = None, payload : Option[String] = None)
                              (implicit request: Request[AnyContent]) : PartialFunction[Throwable, Result] = {
    case e: NoActiveSession => Redirect(appConfig.loginURL, loginParams(hoID, payload))
    case InternalError(e)           =>
      Logger.warn(s"Something went wrong with a call to Auth with exception: ${e}")
      InternalServerError
    case e: AuthorisationException  =>
      Logger.info(s"auth returned $e and redirected user to 'incorrect-account-type' page")
      Redirect(controllers.verification.routes.EmailVerificationController.createGGWAccountAffinityShow())
  }
}