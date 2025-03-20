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

package controllers.reg

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import forms.ConfirmRegistrationEmailForm
import javax.inject.{Inject, Singleton}
import models.{ConfirmRegistrationEmailModel, RegistrationEmailModel}
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{CommonService, EmailVerificationService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import utils.{SCRSExceptions, SessionRegistration}
import views.html.reg.{ConfirmRegistrationEmail => ConfirmRegistrationEmailView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationEmailConfirmationController @Inject()(val emailVerificationService: EmailVerificationService,
                                                        val authConnector: PlayAuthConnector,
                                                        val keystoreConnector: KeystoreConnector,
                                                        val compRegConnector: CompanyRegistrationConnector,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        view: ConfirmRegistrationEmailView)
                                                       (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with CommonService with SCRSExceptions with I18nSupport with SessionRegistration {

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
        emailVerificationService.emailVerifiedStatusInSCRS(regId, () => submitLogic(regId, creds, extId))
      }
    }
  }

  def showLogic(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    for {
      confEmail <- keystoreConnector.fetchAndGet[ConfirmRegistrationEmailModel]("ConfirmEmail")
      regEmail <- keystoreConnector.fetchAndGet[RegistrationEmailModel]("RegEmail")
    } yield {
      val confEmailForm = confEmail.map(x => ConfirmRegistrationEmailForm.form.fill(x)).getOrElse(ConfirmRegistrationEmailForm.form)
      if (regEmail.exists(_.differentEmail.isDefined)) {
        Ok(view(confEmailForm, regEmail.get.differentEmail.get))
      } else {
        Redirect(routes.SignInOutController.postSignIn(None, None, None))
      }
    }
  }

  def submitLogic(regId: String, providerIdFromAuth: String, externalIdFromAuth: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    keystoreConnector.fetchAndGet[RegistrationEmailModel]("RegEmail").flatMap { regEmail =>
      if (!regEmail.exists(_.differentEmail.isDefined)) {
        Future.successful(Redirect(routes.SignInOutController.postSignIn(None, None, None)))
      }
      else {
        val diffEmail = regEmail.get.differentEmail.get
        ConfirmRegistrationEmailForm.form.bindFromRequest().fold(
          hasErrors =>
            Future.successful(BadRequest(view(hasErrors, diffEmail))),
          success =>
            if (success.confirmEmail) {
              emailVerificationService.sendVerificationLink(diffEmail, regId, providerIdFromAuth, externalIdFromAuth)
                .map { emailVerifiedSuccess =>
                  if (emailVerifiedSuccess.contains(true)) {
                    Redirect(routes.CompletionCapacityController.show())
                  } else {
                    Redirect(controllers.verification.routes.EmailVerificationController.verifyShow)
                  }
                }
            } else {
              Future.successful(Redirect(routes.RegistrationEmailController.show))
            }
        )
      }
    }
  }
}