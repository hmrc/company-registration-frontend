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

package controllers.healthcheck

import config.FrontendAppConfig
import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils.SCRSFeatureSwitches
import views.html.healthcheck.HealthCheck

class HealthCheckControllerImpl @Inject()(val scrsFeatureSwitches: SCRSFeatureSwitches,
                                          val appConfig: FrontendAppConfig,
                                          mcc: MessagesControllerComponents) extends HealthCheckController(mcc) {

  override def healthCheckFeature: Boolean = scrsFeatureSwitches.healthCheck.enabled
}

abstract class HealthCheckController(mcc: MessagesControllerComponents) extends FrontendController(mcc) with I18nSupport {

  implicit val appConfig: FrontendAppConfig

  def healthCheckFeature: Boolean

  def checkHealth(status: Option[Int] = None) = Action {
    implicit request =>
      (if (healthCheckFeature) {
        Ok
      } else {
        status.fold(ServiceUnavailable)(new Status(_))
      }) (HealthCheck())
  }
}