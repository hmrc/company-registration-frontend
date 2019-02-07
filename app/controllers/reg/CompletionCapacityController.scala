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
import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse, CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthFunction
import forms.AboutYouForm
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import services.{MetaDataService, MetricsService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SessionRegistration
import views.html.reg.CompletionCapacity

import scala.concurrent.Future

class CompletionCapacityControllerImpl @Inject()(
                                                  val authConnector: PlayAuthConnector,
                                                  val keystoreConnector: KeystoreConnector,
                                                  val businessRegConnector: BusinessRegistrationConnector,
                                                  val metricsService: MetricsService,
                                                  val appConfig: FrontendAppConfig,
                                                  val metaDataService: MetaDataService,
                                                  val compRegConnector: CompanyRegistrationConnector,
                                                  val messagesApi: MessagesApi
                                                ) extends CompletionCapacityController

trait CompletionCapacityController extends FrontendController with AuthFunction with SessionRegistration with I18nSupport {

  val businessRegConnector: BusinessRegistrationConnector
  val metaDataService: MetaDataService
  val metricsService: MetricsService
  implicit val appConfig: FrontendAppConfig

  def show() : Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { regId =>
        businessRegConnector.retrieveMetadata map {
          case BusinessRegistrationSuccessResponse(x) => Ok(CompletionCapacity(AboutYouForm.populateForm(x.completionCapacity.getOrElse("")))) //todo double check empty cc
          case _ => Ok(CompletionCapacity(AboutYouForm.aboutYouFilled))
        }
      }
    }
  }

  def submit() : Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { a =>
        AboutYouForm.form.bindFromRequest.fold(
          errors => Future.successful(BadRequest(CompletionCapacity(errors))),
          success => {
            val context = metricsService.saveCompletionCapacityToCRTimer.time()
            metaDataService.updateCompletionCapacity(success) map {
              _ =>
                context.stop()
                Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails())
            }
          }
        )
      }
    }
  }
}