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
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.TradingDetailsForm
import javax.inject.Inject
import models.{TradingDetailsErrorResponse, TradingDetailsForbiddenResponse, TradingDetailsNotFoundResponse, TradingDetailsSuccessResponse}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import services.{MetricsService, TradingDetailsService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionRegistration
import views.html.reg.TradingDetailsView

import scala.concurrent.Future

class TradingDetailsControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                             val tradingDetailsService: TradingDetailsService,
                                             val metricsService: MetricsService,
                                             val compRegConnector: CompanyRegistrationConnector,
                                             val keystoreConnector: KeystoreConnector,
                                             val appConfig: FrontendAppConfig,
                                             val messagesApi: MessagesApi) extends TradingDetailsController

trait TradingDetailsController extends FrontendController with AuthFunction with ControllerErrorHandler with SessionRegistration with I18nSupport {
  implicit val appConfig: FrontendAppConfig

  val tradingDetailsService : TradingDetailsService
  val metricsService: MetricsService

  val show = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regID =>
        for {
          tradingDetails <- tradingDetailsService.retrieveTradingDetails(regID)
        } yield {
          Ok(TradingDetailsView(TradingDetailsForm.form.fill(tradingDetails)))
        }
      }
    }
  }

  val submit = Action.async { implicit request =>
    ctAuthorised {
      registered { a =>
        TradingDetailsForm.form.bindFromRequest.fold(
          errors => Future.successful(BadRequest(TradingDetailsView(errors))),
          payments => {
            val context = metricsService.saveTradingDetailsToCRTimer.time()
            tradingDetailsService.updateCompanyInformation(payments).map {
              case TradingDetailsSuccessResponse(_) =>
                context.stop()
                Redirect(controllers.handoff.routes.BusinessActivitiesController.businessActivities())
              case TradingDetailsErrorResponse(_) =>
                context.stop()
                BadRequest(defaultErrorPage)
              case TradingDetailsNotFoundResponse =>
                context.stop()
                BadRequest(defaultErrorPage)
              case TradingDetailsForbiddenResponse =>
                context.stop()
                BadRequest(defaultErrorPage)
            }
          }
        )
      }
    }
  }
}