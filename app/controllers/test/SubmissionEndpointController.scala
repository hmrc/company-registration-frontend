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

package controllers.test

import config.AppConfig
import connectors.S4LConnector
import controllers.auth.AuthenticatedController
import forms.SubmissionForm
import javax.inject.{Inject, Singleton}
import models.SubmissionModel
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import views.html.reg.SubmissionEndpoint

import scala.concurrent.ExecutionContext

@Singleton
class SubmissionEndpointController @Inject()(val authConnector: PlayAuthConnector,
                                             val s4LConnector: S4LConnector,
                                             val controllerComponents: MessagesControllerComponents,
                                             view: SubmissionEndpoint)
                                            (implicit val appConfig: AppConfig, implicit val ec: ExecutionContext) extends AuthenticatedController with I18nSupport {

  val getAllS4LEntries: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedOptStr(Retrievals.internalId) { internalID =>
      for {
        fetchSubmission <- s4LConnector.fetchAndGet[SubmissionModel](internalID, "SubmissionData")
        submission = fetchSubmission.getOrElse(SubmissionModel("No submission found", "No submission ref"))
      } yield {
        val submissionForm = SubmissionForm.form().fill(submission)
        Ok(view(submissionForm))
      }
    }
  }
}