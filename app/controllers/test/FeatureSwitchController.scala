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

package controllers.test

import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.controller.FrontendController
import utils.{FeatureSwitch, SCRSFeatureSwitches, SCRSValidators, ValueSetFeatureSwitch}

import scala.concurrent.Future

object FeatureSwitchController extends FeatureSwitchController


trait FeatureSwitchController extends FrontendController {

  val featureSwitch = FeatureSwitch

  def handOffFeatureSwitch(featureName : String, featureState : String) = Action.async {
    implicit request =>

      def feature = featureState match {
        case "stub" | "false"                                      => featureSwitch.disable(FeatureSwitch(featureName, enabled = false))
        case "coho" | "true"                                       => featureSwitch.enable(FeatureSwitch(featureName, enabled = true))
        case date if date.matches(SCRSValidators.datePatternRegex) => featureSwitch.setSystemDate(ValueSetFeatureSwitch(featureName, date))
        case "time-clear"                                          => featureSwitch.setSystemDate(ValueSetFeatureSwitch(featureName, ""))
        case _                                                     => featureSwitch.disable(FeatureSwitch(featureName, enabled = false))
      }

      SCRSFeatureSwitches(featureName) match {
        case Some(_) => {
          val f = feature
          Future.successful(Ok(f.toString))
        }
        case None => Future.successful(BadRequest)
      }
  }

}
