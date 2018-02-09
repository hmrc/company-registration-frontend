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

package controllers.test

import config.FrontendAuthConnector
import connectors.S4LConnector
import controllers.auth.AuthFunction
import forms.SubmissionForm
import models.SubmissionModel
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.MessagesSupport
import views.html.reg.SubmissionEndpoint

object SubmissionEndpointController extends SubmissionEndpointController{
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
}

trait SubmissionEndpointController extends FrontendController with AuthFunction with MessagesSupport {

  val s4LConnector: S4LConnector

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
