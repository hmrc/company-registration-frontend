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

import java.net.URLEncoder

import config.{FrontendAuthConnector, FrontendConfig}
import controllers.auth.AuthFunction
import forms.ReturningUserForm
import models.ReturningUser
import play.api.mvc.Action
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SCRSFeatureSwitches}
import views.html.reg.ReturningUserView

import scala.concurrent.Future

object ReturningUserController extends ReturningUserController with ServicesConfig{
  val createGGWAccountUrl = getConfString("gg-reg-fe.url", throw new Exception("Could not find config for gg-reg-fe url"))
  val eligUri             = getConfString("company-registration-eligibility-frontend.url", throw new Exception("Could not find config for comp-reg-el-fe url"))
  val eligUrl             = s"${baseUrl("company-registration-eligibility-frontend")}$eligUri"
  val compRegFeUrl        = FrontendConfig.self
  val authConnector       = FrontendAuthConnector
}

trait ReturningUserController extends FrontendController with AuthFunction with MessagesSupport {
  val createGGWAccountUrl: String
  val compRegFeUrl: String
  val eligUrl : String

  val show = Action.async { implicit request =>
    onlyIfNotSignedIn {
      val emptyForm = ReturningUserForm.form.fill(ReturningUser(""))
      Future.successful(Ok(ReturningUserView(emptyForm)))
    }
  }
  val submit = Action.async {
    implicit request =>
      ReturningUserForm.form.bindFromRequest.fold(
        errors => Future.successful(BadRequest(ReturningUserView(errors))),
        success => {
          success.returningUser match {
            case "true" => Future.successful(Redirect(buildCreateAccountURL))
            case "false" => Future.successful(Redirect(routes.SignInOutController.postSignIn(None)))
          }
        }
      )
  }

  private[controllers] def buildCreateAccountURL: String = {
    if (signPostingEnabled) {
      eligUrl
    } else {
      val continueUrlUrl = controllers.reg.routes.SignInOutController.postSignIn(None).url
      val ggrf = "government-gateway-registration-frontend"
      val accountType = "accountType=organisation"
      val origin = "origin=company-registration-frontend"
      val continueURL = s"continue=${URLEncoder.encode(s"$compRegFeUrl$continueUrlUrl","UTF-8")}"
      s"$createGGWAccountUrl/${ggrf}?${accountType}&${continueURL}&${origin}"
    }
  }

  def signPostingEnabled: Boolean = SCRSFeatureSwitches.signPosting.enabled
}
