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
import controllers.auth.AuthFunction
import forms.ReturningUserForm
import models.ReturningUser
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Action
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import views.html.reg.ReturningUserView

import scala.concurrent.Future

class ReturningUserControllerImpl @Inject()(val appConfig: FrontendAppConfig,
                                            val authConnector: PlayAuthConnector,
                                            val messagesApi: MessagesApi) extends ReturningUserController {
  lazy val createGGWAccountUrl = appConfig.getConfString("gg-reg-fe.url", throw new Exception("Could not find config for gg-reg-fe url"))
  lazy val eligBaseUrl = appConfig.getConfString(
    "company-registration-eligibility-frontend.url-prefix", throw new Exception("Could not find config for key: company-registration-eligibility-frontend.url-prefix")
  )
  lazy val eligUri = appConfig.getConfString(
    "company-registration-eligibility-frontend.start-url", throw new Exception("Could not find config for key: company-registration-eligibility-frontend.start-url")
  )
 lazy val compRegFeUrl        = appConfig.self
}

trait ReturningUserController extends FrontendController with AuthFunction with I18nSupport {
  implicit val appConfig: FrontendAppConfig
  val createGGWAccountUrl: String
  val compRegFeUrl: String
  val eligBaseUrl : String
  val eligUri : String

  val show = Action.async {
    implicit request => {
      val emptyForm = ReturningUserForm.form.fill(ReturningUser(""))
      Future.successful(Ok(ReturningUserView(emptyForm,hc.authorization.isDefined)))
    }
  }

  val submit = Action.async {
    implicit request => {
         ReturningUserForm.form.bindFromRequest.fold(
          errors => Future.successful(BadRequest(ReturningUserView(errors, hc.authorization.isDefined))),
          success => {
            success.returningUser match {
              case "true" => Future.successful(Redirect(buildCreateAccountURL).withNewSession)
              case "false" => Future.successful(Redirect(routes.SignInOutController.postSignIn(None)))
            }
          }
        )
      }
      }

  private[controllers] def buildCreateAccountURL: String = {
      s"$eligBaseUrl$eligUri"

  }
}