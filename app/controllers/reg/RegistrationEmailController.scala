/*
 * Copyright 2020 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import forms.RegistrationEmailForm
import javax.inject.Inject
import models.Email._
import models.{Email, RegistrationEmailModel}
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{CommonService, EmailVerificationService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import utils.{SCRSExceptions, SessionRegistration}
import views.html.reg.RegistrationEmail

import scala.concurrent.{ExecutionContext, Future}

class RegistrationEmailControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                                val keystoreConnector: KeystoreConnector,
                                                val appConfig: FrontendAppConfig,
                                                val emailVerification: EmailVerificationService,
                                                val compRegConnector: CompanyRegistrationConnector,
                                                val controllerComponents: MessagesControllerComponents)
                                               (implicit val ec: ExecutionContext) extends RegistrationEmailController

abstract class RegistrationEmailController extends AuthenticatedController with CommonService with SCRSExceptions with I18nSupport with SessionRegistration {


  implicit val appConfig: FrontendAppConfig
  val keystoreConnector: KeystoreConnector
  val emailVerification: EmailVerificationService

  val show: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedCompanyContact {
      emailFromAuth =>
        registered { regID =>
          emailVerification.emailVerifiedStatusInSCRS(regID, () => showLogic(emailFromAuth))
        }
    }
  }

  protected def showLogic(emailFromAuth: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    keystoreConnector.fetchAndGet[RegistrationEmailModel]("RegEmail").map { regModelOpt =>
      val populatedForm: Form[RegistrationEmailModel] = RegistrationEmailForm.form.fill(regModelOpt.getOrElse(RegistrationEmailModel("", None)))

      Ok(RegistrationEmail(populatedForm, emailFromAuth))
    }
  }

  val submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedEmailCredsExtId {
      (emailFromAuth, creds, extId) =>
        registered { regID =>
          emailVerification.emailVerifiedStatusInSCRS(regID, () => submitLogic(regID, emailFromAuth, creds.providerId, extId))
        }
    }
  }

  protected def submitLogic(regID: String, emailFromAuth: String, providerIdFromAuth: String, externalIdFromAuth: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    RegistrationEmailForm.form.bindFromRequest().fold(
      hasErrors =>
        Future.successful(BadRequest(RegistrationEmail(hasErrors, emailFromAuth))),
      success =>
        if (success.currentEmail == "currentEmail") {
          scpVerifiedEmail.flatMap {
            case true =>
              updateEmailBlockForSCPUsers(regID, emailFromAuth, providerIdFromAuth, externalIdFromAuth).map { res =>
                Redirect(routes.CompletionCapacityController.show())
              }
            case false =>
              emailVerification.sendVerificationLink(emailFromAuth, regID, providerIdFromAuth, externalIdFromAuth).map { emailVerifiedSuccess =>

                if (emailVerifiedSuccess.getOrElse(false)) {
                  Redirect(routes.CompletionCapacityController.show())
                }
                else {
                  Redirect(controllers.verification.routes.EmailVerificationController.verifyShow())
                }
              }
          }
        } else {
          keystoreConnector.cache[RegistrationEmailModel]("RegEmail", success).map { keystoreOutput =>
            Redirect(routes.RegistrationEmailConfirmationController.show())
          }
        }
    )
  }

  protected def updateEmailBlockForSCPUsers(regId: String, authEmail: String, authProviderId: String, externalId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Option[Email]] = {
    emailVerification.saveEmailBlock(regId, Email(authEmail, SCP, false, true, false), authProviderId, externalId)
  }
}