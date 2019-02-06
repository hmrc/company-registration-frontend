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

package controllers.test

import config.FrontendAppConfig
import connectors.S4LConnector
import controllers.auth.AuthFunction
import forms.SubmissionForm
import javax.inject.Inject
import models.SubmissionModel
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.reg.SubmissionEndpoint

class SubmissionEndpointControllerImpl @Inject()(val authConnector: PlayAuthConnector,
                                                 val s4LConnector: S4LConnector,
                                                 val appConfig: FrontendAppConfig,
                                                 val messagesApi: MessagesApi) extends SubmissionEndpointController

trait SubmissionEndpointController extends FrontendController with AuthFunction with I18nSupport {
  val s4LConnector: S4LConnector

  implicit val appConfig: FrontendAppConfig

  val getAllS4LEntries: Action[AnyContent] = Action.async { implicit request =>
    ctAuthorisedOptStr(Retrievals.internalId) { internalID =>
      for {
        fetchSubmission <- s4LConnector.fetchAndGet[SubmissionModel](internalID, "SubmissionData")
        submission = fetchSubmission.getOrElse(SubmissionModel("No submission found", "No submission ref"))
      } yield {
        val submissionForm = SubmissionForm.form.fill(submission)
        Ok(SubmissionEndpoint(submissionForm))
      }
    }
  }
}