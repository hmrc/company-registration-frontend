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

package controllers.reg

import javax.inject.Inject

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.ConfirmRegistrationEmailForm
import models.{ConfirmRegistrationEmailModel, RegistrationEmailModel}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{CommonService, EmailVerificationService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.{SCRSExceptions, SessionRegistration}
import views.html.reg.ConfirmRegistrationEmail

import scala.concurrent.Future


class RegistrationEmailConfirmationControllerImpl @Inject()(val emailVerificationService: EmailVerificationService,
                                                            val authConnector: PlayAuthConnector,
                                                            val keystoreConnector: KeystoreConnector,
                                                            val appConfig: FrontendAppConfig,
                                                            val compRegConnector: CompanyRegistrationConnector,
                                                            val messagesApi: MessagesApi) extends RegistrationEmailConfirmationController

trait RegistrationEmailConfirmationController extends FrontendController with AuthFunction with CommonService with SCRSExceptions with I18nSupport with SessionRegistration {

  val keystoreConnector: KeystoreConnector
  val emailVerificationService: EmailVerificationService
  implicit val appConfig: FrontendAppConfig

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { regId =>
          emailVerificationService.emailVerifiedStatusInSCRS(regId, () => showLogic)
      }
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedEmailCredsExtId { (email, creds, extId) =>
      registered { regId =>
          emailVerificationService.emailVerifiedStatusInSCRS(regId, () => submitLogic(regId, creds.providerId, extId))
      }
    }
  }

  protected def showLogic(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    for {
      confEmail <- keystoreConnector.fetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail")
      regEmail <- keystoreConnector.fetchAndGet[RegistrationEmailModel]("RegEmail")
    } yield {
      val confEmailForm = confEmail.map(x => ConfirmRegistrationEmailForm.form.fill(x)).getOrElse(ConfirmRegistrationEmailForm.form)
      if (regEmail.exists(_.differentEmail.isDefined)) {
        Ok(ConfirmRegistrationEmail(confEmailForm, regEmail.get.differentEmail.get))
      } else {
        Redirect(routes.SignInOutController.postSignIn(None, None, None))
      }
    }
  }

  protected def submitLogic(regId:String, providerIdFromAuth: String, externalIdFromAuth:String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    keystoreConnector.fetchAndGet[RegistrationEmailModel]("RegEmail").flatMap { regEmail =>
      if (!regEmail.exists(_.differentEmail.isDefined)) {
        Future.successful(Redirect(routes.SignInOutController.postSignIn(None, None, None)))
      }
      else {
        val diffEmail = regEmail.get.differentEmail.get
        ConfirmRegistrationEmailForm.form.bindFromRequest().fold(
          hasErrors =>
            Future.successful(BadRequest(ConfirmRegistrationEmail(hasErrors, diffEmail))),
          success =>
            if (success.confirmEmail) {
              emailVerificationService.sendVerificationLink(diffEmail, regId, providerIdFromAuth, externalIdFromAuth)
                .map { emailVerifiedSuccess =>
                  if (emailVerifiedSuccess.contains(true)) {
                    Redirect(routes.CompletionCapacityController.show())
                  } else {
                    Redirect(controllers.verification.routes.EmailVerificationController.verifyShow())
                  }
                }
            } else {
              Future.successful(Redirect(routes.RegistrationEmailController.show()))
            }
        )
      }
    }
  }
}