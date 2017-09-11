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

package controllers.handoff

import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.handoff.HO6AuthenticationProvider.RegistrationConfirmationController
import forms.errors.DeskproForm
import models.{ConfirmationReferencesSuccessResponse, DESFailureDeskpro, DESFailureRetriable}
import play.api.Logger
import play.api.mvc.{Action, AnyContent, Request}
import services.{HandBackService, NavModelNotFoundException}
import uk.gov.hmrc.play.frontend.auth.connectors.domain.Accounts
import uk.gov.hmrc.play.frontend.auth.{Actions, AuthenticationProvider, GovernmentGateway, TaxRegime}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{DecryptionError, MessagesSupport, PayloadError, SessionRegistration}
import views.html.error_template_restart
import play.api.mvc.Results.Redirect

import scala.concurrent.Future
import scala.util.{Failure, Success}

object RegistrationConfirmationController extends RegistrationConfirmationController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val handBackService = HandBackService
  val companyRegistrationConnector = CompanyRegistrationConnector

}

object  HO6AuthenticationProvider extends GovernmentGateway {
  override val continueURL: String = ""
  override val loginURL: String = ""

  override def redirectToLogin(implicit request: Request[_]) = Future.successful(Redirect(controllers.reg.routes.IncompleteRegistrationController.show))


  case class HO6Regime(redirectUrl: String) extends TaxRegime {
    override def isAuthorised(accounts: Accounts): Boolean = true

    //accounts.ct.isDefined
    override def authenticationType: AuthenticationProvider = HO6AuthenticationProvider
  }

  trait RegistrationConfirmationController extends FrontendController with Actions with MessagesSupport with SessionRegistration {

    val handBackService: HandBackService

    //HO6
    def registrationConfirmation(requestData: String): Action[AnyContent] = AuthorisedFor(taxRegime = HO6Regime(""), pageVisibility = GGConfidence).async {
      implicit user =>
        implicit request =>
          registered {
            regid =>
              handBackService.decryptConfirmationHandback(requestData) flatMap {
                case Success(s) => handBackService.storeConfirmationHandOff(s, regid).map {
                  case ConfirmationReferencesSuccessResponse(_) => Redirect(controllers.reg.routes.ConfirmationControllerImpl.show())
                  case DESFailureRetriable => Redirect(controllers.reg.routes.ConfirmationControllerImpl.resubmitPage())
                  case _ => Redirect(controllers.reg.routes.ConfirmationControllerImpl.deskproPage())
                }
                case Failure(DecryptionError) => Future.successful(BadRequest(error_template_restart("6", "DecryptionError")))
                case unknown => Future.successful{
                  Logger.warn(s"[RegistrationConfirmationController][registrationConfirmation] HO6 Unexpected result, sending to post-sign-in : ${unknown}")
                  Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
                }
              } recover {
                case ex: NavModelNotFoundException => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
              }
          }
    }
  }

}
