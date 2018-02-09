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
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.AccountingDatesForm
import models.{AccountingDatesModel, AccountingDetailsNotFoundResponse, AccountingDetailsSuccessResponse}
import org.joda.time.LocalDate
import play.api.mvc.Action
import services.{AccountingService, MetricsService, TimeService}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SessionRegistration}
import views.html.reg.AccountingDates

import scala.concurrent.Future

object AccountingDatesController extends AccountingDatesController {
  val authConnector = FrontendAuthConnector
  val accountingService = AccountingService
  override val metricsService: MetricsService = MetricsService
  val timeService: TimeService = TimeService
  val companyRegistrationConnector = CompanyRegistrationConnector
  val keystoreConnector = KeystoreConnector
}

trait AccountingDatesController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with MessagesSupport {

  val accountingService : AccountingService
  val metricsService: MetricsService
  val timeService: TimeService

  implicit val bHS = TimeService.bHS

  val show = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { _ =>
        accountingService.fetchAccountingDetails.map {
          accountingDetails => {
            Ok(AccountingDates(AccountingDatesForm.form.fill(accountingDetails), timeService.futureWorkingDate(LocalDate.now, 60)))
          }
        }
      }
    }
  }

  val submit = Action.async { implicit request =>
    ctAuthorised {
      AccountingDatesForm.form.bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(AccountingDates(formWithErrors, timeService.futureWorkingDate(LocalDate.now, 60))))
        }, {
          val context = metricsService.saveAccountingDatesToCRTimer.time()
          data => {
            val updatedData = data.crnDate match {
              case "whenRegistered" => data.copy(crnDate = AccountingDatesModel.WHEN_REGISTERED, day = None, month = None, year = None)
              case "futureDate" => data.copy(crnDate = AccountingDatesModel.FUTURE_DATE)
              case "notPlanningToYet" => data.copy(crnDate = AccountingDatesModel.NOT_PLANNING_TO_YET, day = None, month = None, year = None)
            }
            accountingService.updateAccountingDetails(updatedData) map {
              case AccountingDetailsSuccessResponse(_) =>
                context.stop()
                Redirect(routes.TradingDetailsController.show())
              case AccountingDetailsNotFoundResponse =>
                context.stop()
                NotFound(defaultErrorPage)
              case _ =>
                context.stop()
                BadRequest(defaultErrorPage)
            }
          }
        }
      )
    }
  }
}
