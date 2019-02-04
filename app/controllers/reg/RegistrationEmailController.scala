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

import config.{AppConfig, FrontendAppConfig, FrontendAuthConnector}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.RegistrationEmailForm
import models.RegistrationEmailModel
import play.api.data.Form
import play.api.mvc._
import services.{CommonService, EmailVerificationService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SCRSExceptions, SCRSFeatureSwitches, SessionRegistration}
import views.html.reg.RegistrationEmail

import scala.concurrent.{Await, Future}

object RegistrationEmailController extends RegistrationEmailController with ServicesConfig {
  val authConnector = FrontendAuthConnector
  val scrsFeatureSwitches = SCRSFeatureSwitches
  val keystoreConnector = KeystoreConnector
  override val appConfig = FrontendAppConfig
  val emailVerification = EmailVerificationService
  val companyRegistrationConnector: CompanyRegistrationConnector = CompanyRegistrationConnector
}

trait RegistrationEmailController extends FrontendController with AuthFunction with CommonService with SCRSExceptions with MessagesSupport with SessionRegistration {


  implicit val appConfig: AppConfig
  val keystoreConnector: KeystoreConnector
  val emailVerification: EmailVerificationService
  val scrsFeatureSwitches : SCRSFeatureSwitches
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
    ctAuthorisedCompanyContact {
      emailFromAuth =>
        registered { regID =>
          emailVerification.emailVerifiedStatusInSCRS(regID, () => submitLogic(regID, emailFromAuth))
        }
    }
  }

  protected def submitLogic(regID:String, emailFromAuth:String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    RegistrationEmailForm.form.bindFromRequest().fold(
      hasErrors =>
        Future.successful(BadRequest(RegistrationEmail(hasErrors, emailFromAuth))),
      success =>

        if (success.currentEmail == "currentEmail") {
          emailVerification.sendVerificationLink(emailFromAuth, regID)
            .flatMap { emailVerifiedSuccess =>
              scpVerifiedEmail(sCPEnabledFeature).map { ver =>
                if (emailVerifiedSuccess.getOrElse(false) || ver) {
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
 protected def sCPEnabledFeature = {
    scrsFeatureSwitches("sCPEnabled") match {
      case Some(fs) => fs.enabled
      case _ => false
    }
  }

}