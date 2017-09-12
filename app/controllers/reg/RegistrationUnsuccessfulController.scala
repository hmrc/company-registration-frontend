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

package controllers.reg

import config.FrontendAuthConnector
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import controllers.auth.SCRSRegime
import uk.gov.hmrc.play.frontend.controller.FrontendController
import services.DeleteSubmissionService

import scala.concurrent.Future
import views.html.reg.RegistrationUnsuccessful
import uk.gov.hmrc.play.frontend.auth.Actions
import utils.{SessionRegistration, MessagesSupport}


object RegistrationUnsuccessfulController extends RegistrationUnsuccessfulController {
  val authConnector = FrontendAuthConnector
  val keystoreConnector = KeystoreConnector
  val deleteSubService = DeleteSubmissionService
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait RegistrationUnsuccessfulController extends FrontendController with Actions with SessionRegistration with MessagesSupport {

  val deleteSubService: DeleteSubmissionService

  def show = AuthorisedFor(taxRegime = SCRSRegime("post-sign-in"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        Future.successful(Ok(RegistrationUnsuccessful()))

  }

  def submit = AuthorisedFor(taxRegime = SCRSRegime(""), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { regId =>
          deleteSubService.deleteSubmission(regId) flatMap {
            case true => keystoreConnector.remove() map {
              _ => Redirect(controllers.reg.routes.SignInOutController.postSignIn(None))
              }
            case false => Future.successful(InternalServerError)

          }
        }
  }
}
