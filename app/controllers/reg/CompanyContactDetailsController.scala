/*
 * Copyright 2018 HM Revenue & Customs
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
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.AuthFunction
import forms.CompanyContactForm
import models.{CompanyContactDetailsBadRequestResponse, CompanyContactDetailsForbiddenResponse, CompanyContactDetailsNotFoundResponse, CompanyContactDetailsSuccessResponse}
import play.api.mvc.Action
import services.{CompanyContactDetailsService, MetricsService}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.reg.CompanyContactDetails

import scala.concurrent.Future

object CompanyContactDetailsController extends CompanyContactDetailsController {
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
  val companyContactDetailsService = CompanyContactDetailsService
  val metricsService: MetricsService = MetricsService
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
  override val appConfig: AppConfig  = FrontendAppConfig
}

trait CompanyContactDetailsController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with MessagesSupport {

  val s4LConnector: S4LConnector
  val companyContactDetailsService: CompanyContactDetailsService
  val metricsService: MetricsService
  implicit val appConfig: AppConfig

  val show = Action.async {
    implicit request =>
      ctAuthorisedCompanyContact { companyContactAuth =>
        checkStatus { _ =>
          companyContactDetailsService.fetchContactDetails(companyContactAuth).map {
            contactDetails => Ok(CompanyContactDetails(CompanyContactForm.form.fill(contactDetails)))
          }
        }
      }
  }

  val submit = Action.async {
    implicit request =>
      ctAuthorisedCompanyContactAmend { (ccAuth, cred, eID) =>
        registered { regId =>
          CompanyContactForm.form.bindFromRequest().fold(
            hasErrors => Future.successful(BadRequest(CompanyContactDetails(hasErrors))),
            data => {
              val context = metricsService.saveContactDetailsToCRTimer.time()
              companyContactDetailsService.updateContactDetails(data) flatMap {
                case CompanyContactDetailsSuccessResponse(details) =>
                  context.stop()
                  companyContactDetailsService.checkIfAmendedDetails(ccAuth, cred, eID, details).flatMap { _ =>
                    companyContactDetailsService.updatePrePopContactDetails(regId, details) map { _ =>
                      Redirect(routes.AccountingDatesController.show())
                    }
                  }
                case CompanyContactDetailsNotFoundResponse => Future.successful(NotFound(defaultErrorPage))
                case CompanyContactDetailsBadRequestResponse => Future.successful(BadRequest(defaultErrorPage))
                case CompanyContactDetailsForbiddenResponse => Future.successful(Forbidden(defaultErrorPage))
                case _ => Future.successful(InternalServerError(defaultErrorPage))
              }
            }
          )
        }
      }
  }
}
