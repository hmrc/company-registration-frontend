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

package controllers.reg

import config.AppConfig
import controllers.auth.AuthenticatedController
import forms.ReturningUserForm

import javax.inject.{Inject, Singleton}
import models.ReturningUser
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import views.html.reg.ReturningUserView

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReturningUserController @Inject()(val authConnector: PlayAuthConnector,
                                        val controllerComponents: MessagesControllerComponents,
                                        view : ReturningUserView)(implicit val ec: ExecutionContext, implicit val appConfig: AppConfig) extends AuthenticatedController with I18nSupport {
  lazy val createGGWAccountUrl: String = appConfig.servicesConfig.getConfString("gg-reg-fe.url", throw new Exception("Could not find config for gg-reg-fe url"))
  lazy val eligBaseUrl: String = appConfig.servicesConfig.getConfString(
    "company-registration-eligibility-frontend.url-prefix", throw new Exception("Could not find config for key: company-registration-eligibility-frontend.url-prefix")
  )
  lazy val eligUri: String = appConfig.servicesConfig.getConfString(
    "company-registration-eligibility-frontend.start-url", throw new Exception("Could not find config for key: company-registration-eligibility-frontend.start-url")
  )
  lazy val compRegFeUrl: String = appConfig.self


  def show: Action[AnyContent] = Action.async {
    implicit request => {
      val emptyForm = ReturningUserForm.form.fill(ReturningUser(""))
      Future.successful(Ok(view(emptyForm, hc.authorization.isDefined)))
    }
  }

  def submit: Action[AnyContent] = Action.async {
    implicit request => {
      ReturningUserForm.form.bindFromRequest().fold(
        errors => Future.successful(BadRequest(view(errors, hc.authorization.isDefined))),
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