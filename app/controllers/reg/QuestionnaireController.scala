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

import config.{AppConfig, FrontendAppConfig}
import forms.QuestionnaireForm
import play.api.mvc.Action
import services.{MetricsService, QuestionnaireService}
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.MessagesSupport
import views.html.reg.Questionnaire

import scala.concurrent.Future

object QuestionnaireController extends QuestionnaireController {
  val metricsService = MetricsService
  val qService = QuestionnaireService
  override val appConfig =  FrontendAppConfig
}

trait QuestionnaireController extends FrontendController with MessagesSupport {
  implicit val appConfig: AppConfig
  val  metricsService : MetricsService
  val qService: QuestionnaireService
  val show = Action.async {
    implicit request =>
      Future.successful(Ok(Questionnaire(QuestionnaireForm.formFilled)))
  }

  val submit = Action.async {
    implicit request =>
      QuestionnaireForm.form.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest(Questionnaire(errors))),
        success => {
          metricsService.numberOfQuestionnairesSubmitted.inc()
          qService.sendAuditEventOnSuccessfulSubmission(success)
          Future.successful(Redirect(controllers.reg.routes.WelcomeController.show()))
        }
      )
  }
}
