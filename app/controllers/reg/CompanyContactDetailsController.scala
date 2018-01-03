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

import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import controllers.auth.SCRSRegime
import forms.CompanyContactForm
import models.{CompanyContactDetailsBadRequestResponse, CompanyContactDetailsForbiddenResponse, CompanyContactDetailsNotFoundResponse, CompanyContactDetailsSuccessResponse}
import services.{CompanyContactDetailsService, MetricsService}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.reg.CompanyContactDetails
import utils.{MessagesSupport, SessionRegistration}

import scala.concurrent.Future

object CompanyContactDetailsController extends CompanyContactDetailsController {
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
  val companyContactDetailsService = CompanyContactDetailsService
  val metricsService: MetricsService = MetricsService
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
}

trait CompanyContactDetailsController extends FrontendController with Actions with ControllerErrorHandler with SessionRegistration with MessagesSupport {

  val s4LConnector: S4LConnector
  val companyContactDetailsService: CompanyContactDetailsService
  val metricsService: MetricsService

  val show = AuthorisedFor(SCRSRegime("first-hand-off"), GGConfidence).async {
    implicit user =>
      implicit request =>
        checkStatus { _ =>
          companyContactDetailsService.fetchContactDetails.map {
            contactDetails => Ok(CompanyContactDetails(CompanyContactForm.form.fill(contactDetails)))
          }
        }
  }

  val submit = AuthorisedFor(SCRSRegime("first-hand-off"), GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { regId =>
          CompanyContactForm.form.bindFromRequest().fold(
            hasErrors => Future.successful(BadRequest(CompanyContactDetails(hasErrors))),
            data => {
              val context = metricsService.saveContactDetailsToCRTimer.time()
              companyContactDetailsService.updateContactDetails(data) flatMap {
                case CompanyContactDetailsSuccessResponse(details) =>
                  context.stop()
                  companyContactDetailsService.checkIfAmendedDetails(details).flatMap { _ =>
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
