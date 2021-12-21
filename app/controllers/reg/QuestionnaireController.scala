/*
 * Copyright 2021 HM Revenue & Customs
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
import forms.QuestionnaireForm
import javax.inject.{Inject, Singleton}
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import services.{MetricsService, QuestionnaireService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.reg.{Questionnaire => QuestionnaireView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class QuestionnaireController @Inject()(val metricsService: MetricsService,
                                        val qService: QuestionnaireService,
                                        mcc: MessagesControllerComponents,
                                        view: QuestionnaireView)(implicit val appConfig: FrontendAppConfig, implicit val ec: ExecutionContext) extends FrontendController(mcc) with I18nSupport {


  lazy val gHost = appConfig.govHostUrl
  val show = Action.async {
    implicit request =>
      Future.successful(Ok(view(QuestionnaireForm.formFilled)))
  }

  val submit = Action.async {
    implicit request =>
      QuestionnaireForm.form.bindFromRequest.fold(
        errors =>
          Future.successful(BadRequest(view(errors))),
        success => {
          metricsService.numberOfQuestionnairesSubmitted.inc()
          qService.sendAuditEventOnSuccessfulSubmission(success)
          Future.successful(Redirect(gHost))
        }
      )
  }
}