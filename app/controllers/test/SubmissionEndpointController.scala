/*
 * Copyright 2017 HM Revenue & Customs
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
import controllers.auth.SCRSRegime
import forms.SubmissionForm
import models.{SubmissionModel, UserIDs}
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import views.html.reg.SubmissionEndpoint
import utils.MessagesSupport

object SubmissionEndpointController extends SubmissionEndpointController{
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
}

trait SubmissionEndpointController extends FrontendController with Actions with MessagesSupport {

  val s4LConnector: S4LConnector

  val getAllS4LEntries = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-submission"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        for{
          userIds <- authConnector.getIds[UserIDs](user)
          fetchSubmission <- s4LConnector.fetchAndGet[SubmissionModel](userIds.internalId, "SubmissionData")
          submission = fetchSubmission.getOrElse(SubmissionModel("No submission found", "No submission ref"))
        } yield {
          val submissionForm = SubmissionForm.form.fill(submission)
          Ok(SubmissionEndpoint(submissionForm))
        }
  }
}
