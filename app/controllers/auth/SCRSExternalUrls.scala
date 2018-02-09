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

package controllers.auth

import controllers.reg.routes
import play.api.Play
import play.api.Play.current
import uk.gov.hmrc.play.config.{RunMode, ServicesConfig}

object SCRSExternalUrls extends RunMode with ServicesConfig {
  private[SCRSExternalUrls] val companyAuthHost = Play.configuration.getString(s"microservice.services.auth.company-auth.url").getOrElse("")
  private[SCRSExternalUrls] val loginCallback = Play.configuration.getString(s"microservice.services.auth.login-callback.url").getOrElse("")
  private[SCRSExternalUrls] val loginPath = Play.configuration.getString(s"microservice.services.auth.login_path").getOrElse("")
  private[SCRSExternalUrls] val logoutPath = Play.configuration.getString(s"microservice.services.auth.logout_path").getOrElse("")

  val loginURL = s"$companyAuthHost$loginPath"
  val logoutURL = s"$companyAuthHost$logoutPath"
  def continueURL(handOffID: Option[String], payload: Option[String]) = s"$loginCallback${routes.SignInOutController.postSignIn(None, handOffID, payload).url}"
}
