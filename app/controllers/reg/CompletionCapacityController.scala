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
import connectors.{BusinessRegistrationConnector, BusinessRegistrationSuccessResponse, CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.AuthenticatedController
import forms.AboutYouForm
import javax.inject.{Inject, Singleton}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{MetaDataService, MetricsService}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import utils.SessionRegistration
import views.html.reg.{CompletionCapacity => CompletionCapacityView}
import uk.gov.hmrc.http.HttpReads.Implicits

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompletionCapacityController @Inject()(
                                              val authConnector: PlayAuthConnector,
                                              val keystoreConnector: KeystoreConnector,
                                              val businessRegConnector: BusinessRegistrationConnector,
                                              val metricsService: MetricsService,
                                              val metaDataService: MetaDataService,
                                              val compRegConnector: CompanyRegistrationConnector,
                                              val controllerComponents: MessagesControllerComponents,
                                              view: CompletionCapacityView
                                            )(implicit val ec: ExecutionContext, implicit val appConfig: AppConfig) extends AuthenticatedController with SessionRegistration with I18nSupport {


  def show(): Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      checkStatus { _ =>
        businessRegConnector.retrieveMetadata map {
          case BusinessRegistrationSuccessResponse(response) => {
            response.completionCapacity match {
              case Some(cc) =>  Ok(view(AboutYouForm.populateForm(cc)))
              case _ =>  Ok(view(AboutYouForm.aboutYouFilled))
            }
          }
          case _ => Ok(view(AboutYouForm.aboutYouFilled))
        }
      }
    }
  }

  def submit: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorised {
      registered { _ =>
        AboutYouForm.form.bindFromRequest().fold(
          errors => Future.successful(BadRequest(view(errors))),
          success => {
            val context = metricsService.saveCompletionCapacityToCRTimer.time()
            metaDataService.updateCompletionCapacity(success) map {
              _ =>
                context.stop()
                Redirect(controllers.handoff.routes.BasicCompanyDetailsController.basicCompanyDetails)
            }
          }
        )
      }
    }
  }
}