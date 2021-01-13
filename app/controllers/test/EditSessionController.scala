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

package controllers.test

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, Controller}
import uk.gov.hmrc.http.SessionKeys

class EditSessionControllerImpl @Inject()() extends EditSessionController

trait EditSessionController extends Controller {

  def editSession(newSessionId: String): Action[AnyContent] = Action {
    implicit request =>
      val newData = request.session.data.updated(SessionKeys.sessionId, newSessionId)
      val newSession = request.session.copy(data = newData)

      Ok(s"Session id set to $newSessionId").withSession(newSession)
  }
}