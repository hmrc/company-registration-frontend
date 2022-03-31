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

package controllers.reg

import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.AuthenticatedController
import forms.CompanyContactForm
import javax.inject.{Inject, Singleton}
import models.{CompanyContactDetailsBadRequestResponse, CompanyContactDetailsForbiddenResponse, CompanyContactDetailsNotFoundResponse, CompanyContactDetailsSuccessResponse}
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import services.{CompanyContactDetailsService, MetricsService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.{SCRSFeatureSwitches, SessionRegistration}
import views.html.reg.{CompanyContactDetails => CompanyContactDetailsView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyContactDetailsController @Inject()(val authConnector: PlayAuthConnector,
                                                val s4LConnector: S4LConnector,
                                                val metricsService: MetricsService,
                                                val compRegConnector: CompanyRegistrationConnector,
                                                val keystoreConnector: KeystoreConnector,
                                                val companyContactDetailsService: CompanyContactDetailsService,
                                                val scrsFeatureSwitches: SCRSFeatureSwitches,
                                                val controllerComponents: MessagesControllerComponents,
                                                val controllerErrorHandler: ControllerErrorHandler,
                                                view: CompanyContactDetailsView)
                                               (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext) extends AuthenticatedController with SessionRegistration with I18nSupport {


  val show = Action.async {
    implicit request =>
      ctAuthorised {
        checkStatus { regId =>
          for {
            contactDetails <- companyContactDetailsService.fetchContactDetails
            companyName <- compRegConnector.fetchCompanyName(regId)
          } yield Ok(view(CompanyContactForm.form.fill(contactDetails), companyName))
        }
      }
  }

  val submit = Action.async {
    implicit request =>
      ctAuthorisedEmailCredsExtId { (email, cred, eID) =>
        registered { regId =>
          CompanyContactForm.form.bindFromRequest().fold(
            hasErrors =>
              compRegConnector.fetchCompanyName(regId).map(cName => BadRequest(view(hasErrors, cName))),
            data => {
              val context = metricsService.saveContactDetailsToCRTimer.time()
              companyContactDetailsService.updateContactDetails(data) flatMap {
                case CompanyContactDetailsSuccessResponse(details) =>
                  context.stop()
                  companyContactDetailsService.checkIfAmendedDetails(email, cred, eID, details).flatMap { _ =>
                    companyContactDetailsService.updatePrePopContactDetails(regId, models.CompanyContactDetails.toApiModel(details)) map { _ =>
                      if (scrsFeatureSwitches.takeovers.enabled) {
                        Redirect(controllers.takeovers.routes.ReplacingAnotherBusinessController.show)
                      }
                      else {
                        Redirect(routes.AccountingDatesController.show)
                      }
                    }
                  }
                case CompanyContactDetailsNotFoundResponse => Future.successful(NotFound(controllerErrorHandler.defaultErrorPage))
                case CompanyContactDetailsBadRequestResponse => Future.successful(BadRequest(controllerErrorHandler.defaultErrorPage))
                case CompanyContactDetailsForbiddenResponse => Future.successful(Forbidden(controllerErrorHandler.defaultErrorPage))
                case _ => Future.successful(InternalServerError(controllerErrorHandler.defaultErrorPage))
              }
            }
          )
        }
      }
  }
}