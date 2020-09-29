/*
 * Copyright 2020 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import utils._

import scala.concurrent.Future

class FeatureSwitchControllerImpl @Inject()(val scrsFeatureSwitches: SCRSFeatureSwitches,
                                            val featureSwitchManager: FeatureSwitchManager,
                                            mcc: MessagesControllerComponents) extends FeatureSwitchController(mcc)

abstract class FeatureSwitchController(mcc: MessagesControllerComponents) extends FrontendController(mcc) {

  val scrsFeatureSwitches: SCRSFeatureSwitches
  val featureSwitchManager: FeatureSwitchManager

  def handOffFeatureSwitch(featureName: String, featureState: String) = Action.async {
    implicit request =>

      def feature = featureState match {
        case "stub" | "false" => featureSwitchManager.disable(BooleanFeatureSwitch(featureName, enabled = false))
        case "coho" | "true" => featureSwitchManager.enable(BooleanFeatureSwitch(featureName, enabled = true))
        case date if date.matches(SCRSValidators.datePatternRegex) => featureSwitchManager.setSystemDate(ValueSetFeatureSwitch(featureName, date))
        case "time-clear" => featureSwitchManager.setSystemDate(ValueSetFeatureSwitch(featureName, ""))
        case _ => featureSwitchManager.disable(BooleanFeatureSwitch(featureName, enabled = false))
      }

      scrsFeatureSwitches(featureName) match {
        case Some(_) => {
          val f = feature
          Future.successful(Ok(f.toString))
        }
        case None => Future.successful(BadRequest)
      }
  }
}