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

package controllers.healthcheck

import config.{AppConfig, FrontendAppConfig}
import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{MessagesSupport, SCRSFeatureSwitches}
import views.html.healthcheck.HealthCheck

object HealthCheckController extends HealthCheckController {
  override val appConfig = FrontendAppConfig
  override def healthCheckFeature: Boolean = SCRSFeatureSwitches.healthCheck.enabled
}

trait HealthCheckController extends FrontendController with MessagesSupport {

  implicit val appConfig: AppConfig

  def checkHealth(status: Option[Int] = None) = Action {
    implicit request =>
      (if(healthCheckFeature) {
        Ok
      } else {
        status.fold(ServiceUnavailable)(new Status(_))
      })(HealthCheck())
  }


  def healthCheckFeature: Boolean
}
