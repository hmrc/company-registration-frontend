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

package controllers.verification

import config.{FrontendAuthConnector, FrontendConfig}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.SCRSRegime
import play.api.mvc.Action
import uk.gov.hmrc.play.binders.ContinueUrl
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.verification.{CreateGGWAccount, CreateNewGGWAccount, createNewAccount, verifyYourEmail}
import uk.gov.hmrc.play.frontend.auth.Actions

import scala.concurrent.Future

object EmailVerificationController extends EmailVerificationController with ServicesConfig {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val createGGWAccountUrl = getConfString("gg-reg-fe.url", throw new Exception("Could not find config for gg-reg-fe url"))
  val callbackUrl = getConfString("auth.login-callback.url", throw new Exception("Could not find config for callback url"))
  val frontEndUrl=FrontendConfig.self
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait EmailVerificationController extends FrontendController with Actions with SessionRegistration with MessagesSupport {

  val keystoreConnector : KeystoreConnector
  val createGGWAccountUrl: String
  val callbackUrl: String
  val frontEndUrl : String

  val verifyShow = AuthorisedFor(taxRegime = SCRSRegime("verify-your-email"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        keystoreConnector.fetchAndGet[String]("email") map {
          case Some(email) => Ok(verifyYourEmail(email))
          case None => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
        }
  }

  val verifySubmit = Action.async { implicit request =>
    Future.successful(Ok)
  }

  val createShow = Action.async {
    implicit request =>
      Future.successful(Ok(createNewAccount()))
  }

  val createSubmit = Action.async { implicit request =>
    //TODO change this to create account - need to remove auth context from session / logout
    Future.successful(Redirect(controllers.reg.routes.WelcomeController.show()))
  }

  val createGGWAccountAffinityShow = Action.async {
    implicit request =>
      val redirect = controllers.reg.routes.WelcomeController.show().url
      val url = controllers.reg.routes.SignInOutController.signOut(Some(ContinueUrl(s"$frontEndUrl$redirect"))).url
      Future.successful(Ok(CreateGGWAccount(url)))

  }

  val createGGWAccountSubmit = Action.async { implicit request =>
    //TODO change this to create account - need to remove auth context from session / logout
    Future.successful(Redirect(controllers.reg.routes.SignInOutController.signOut(None)).withNewSession)
  }

  val createNewGGWAccountShow = Action.async {
    implicit request =>
      val redirect = controllers.reg.routes.WelcomeController.show().url
      val url = controllers.reg.routes.SignInOutController.signOut(Some(ContinueUrl(s"$frontEndUrl$redirect"))).url
      Future.successful(Ok(CreateNewGGWAccount(url)))
  }

  val startAgain = Action.async {
    implicit request =>
      Future.successful(Redirect(controllers.reg.routes.WelcomeController.show()).withNewSession)
  }
}
