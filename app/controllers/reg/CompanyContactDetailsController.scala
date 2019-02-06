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

import config.FrontendAppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.AuthFunction
import forms.CompanyContactForm
import javax.inject.Inject
import models.{CompanyContactDetailsBadRequestResponse, CompanyContactDetailsForbiddenResponse, CompanyContactDetailsNotFoundResponse, CompanyContactDetailsSuccessResponse}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import services.{CompanyContactDetailsService, MetricsService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionRegistration
import views.html.reg.CompanyContactDetails

import scala.concurrent.Future

class CompanyContactDetailsControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                                    val s4LConnector: S4LConnector,
                                                    val metricsService: MetricsService,
                                                    val compRegConnector: CompanyRegistrationConnector,
                                                    val keystoreConnector: KeystoreConnector,
                                                    val appConfig: FrontendAppConfig,
                                                    val companyContactDetailsService: CompanyContactDetailsService,
                                                    val messagesApi: MessagesApi) extends CompanyContactDetailsController

trait CompanyContactDetailsController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {

  val s4LConnector: S4LConnector
  val companyContactDetailsService: CompanyContactDetailsService
  val compRegConnector: CompanyRegistrationConnector
  val metricsService: MetricsService
  implicit val appConfig: FrontendAppConfig

  val show = Action.async {
    implicit request =>
      ctAuthorisedCompanyContact { companyContactAuth =>
        checkStatus { regId =>
          for {
            contactDetails  <- companyContactDetailsService.fetchContactDetails(companyContactAuth)
            companyName     <- compRegConnector.fetchCompanyName(regId)
          } yield Ok(CompanyContactDetails(CompanyContactForm.form.fill(contactDetails),companyName))
        }
      }
  }

  val submit = Action.async {
    implicit request =>
      ctAuthorisedCompanyContactAmend { (email, cred, eID) =>
        registered { regId =>
          CompanyContactForm.form.bindFromRequest().fold(
            hasErrors =>
              compRegConnector.fetchCompanyName(regId).map(cName => BadRequest(CompanyContactDetails(hasErrors, cName))),
            data => {
              val context = metricsService.saveContactDetailsToCRTimer.time()
              companyContactDetailsService.updateContactDetails(data) flatMap {
                case CompanyContactDetailsSuccessResponse(details) =>
                  context.stop()
                  companyContactDetailsService.checkIfAmendedDetails(email, cred, eID, details).flatMap { _ =>
                    companyContactDetailsService.updatePrePopContactDetails(regId, models.CompanyContactDetails.toApiModel(details)) map { _ =>
                      Redirect(routes.AccountingDatesController.show())
                    }
                  }
                case CompanyContactDetailsNotFoundResponse      => Future.successful(NotFound(defaultErrorPage))
                case CompanyContactDetailsBadRequestResponse    => Future.successful(BadRequest(defaultErrorPage))
                case CompanyContactDetailsForbiddenResponse     => Future.successful(Forbidden(defaultErrorPage))
                case _                                          => Future.successful(InternalServerError(defaultErrorPage))
              }
            }
          )
        }
      }
  }
}