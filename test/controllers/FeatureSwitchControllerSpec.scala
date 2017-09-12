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

package controllers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import controllers.test.FeatureSwitchController
import org.scalatest.BeforeAndAfterEach
import play.api.libs.openid.Errors.BAD_RESPONSE
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import play.api.test.Helpers._
import utils.BooleanFeatureSwitch

class FeatureSwitchControllerSpec extends UnitSpec with BeforeAndAfterEach {

  implicit val system = ActorSystem("test")
  implicit def mat: Materializer = ActorMaterializer()

  override def beforeEach() {
    System.clearProperty("feature.cohoFirstHandOff")
    System.clearProperty("feature.businessActivitiesHandOff")
  }

  class Setup {
    val controller = FeatureSwitchController
  }

  "handOffFeatureSwitch" should {

    "return a first handoff feature state set to false when we specify stub" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "stub"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(cohoFirstHandOff,false)"
    }

    "return a first handoff feature state set to true when we specify coho" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "coho"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(cohoFirstHandOff,true)"
    }

    "return a Business Activities hand-off feature state set to false when we specify stub" in new Setup {
      val featureName = "businessActivitiesHandOff"
      val featureState = "stub"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(businessActivitiesHandOff,false)"
    }

    "return a Business Activities handoff feature state set to true when we specify coho" in new Setup {
      val featureName = "businessActivitiesHandOff"
      val featureState = "coho"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(businessActivitiesHandOff,true)"
    }


    "return a Business Activities hand-off feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = "businessActivitiesHandOff"
      val featureState = "xxxx"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(businessActivitiesHandOff,false)"
    }

    "return a first handoff feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "xxxx"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())

      status(result) shouldBe OK
      bodyOf(await(result)) shouldBe "BooleanFeatureSwitch(cohoFirstHandOff,false)"
    }


    "return a first handoff feature state set to false as a default when we specify a non implemented feature name" in new Setup {
      val featureName = "Rubbish"
      val featureState = "coho"

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())

      status(result) shouldBe BAD_REQUEST


    }

  }

}
