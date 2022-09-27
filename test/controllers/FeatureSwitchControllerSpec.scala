/*
 * Copyright 2022 HM Revenue & Customs
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
import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

class FeatureSwitchControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  implicit val system = ActorSystem("test")

  implicit def mat: Materializer = ActorMaterializer()

  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {
    resetMocks()
    val controller = new FeatureSwitchController(mockMcc) {
      override val scrsFeatureSwitches: SCRSFeatureSwitches = mockSCRSFeatureSwitches
      override val featureSwitchManager = mockFeatureSwitchManager
    }
  }

  "handOffFeatureSwitch" should {

    "return a first handoff feature state set to false when we specify stub" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "stub"

      when(mockSCRSFeatureSwitches.apply(ArgumentMatchers.any[String])).thenReturn(Some(BooleanFeatureSwitch("cohoFirstHandOff", false)))
      when(mockFeatureSwitchManager.disable(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("cohoFirstHandOff", false))
      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) mustBe OK
      bodyOf(await(result)) mustBe "BooleanFeatureSwitch(cohoFirstHandOff,false)"
    }

    "return a first handoff feature state set to true when we specify coho" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "coho"
      when(mockSCRSFeatureSwitches.apply(ArgumentMatchers.any[String])).thenReturn(Some(BooleanFeatureSwitch("cohoFirstHandOff", true)))
      when(mockFeatureSwitchManager.enable(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("cohoFirstHandOff", true))

      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())
      status(result) mustBe OK
      bodyOf(await(result)) mustBe "BooleanFeatureSwitch(cohoFirstHandOff,true)"
    }

    "return a first handoff feature state set to false as a default when we specify xxxx" in new Setup {
      val featureName = "cohoFirstHandOff"
      val featureState = "xxxx"
      when(mockSCRSFeatureSwitches.apply(ArgumentMatchers.any[String])).thenReturn(Some(BooleanFeatureSwitch("cohoFirstHandOff", false)))
      when(mockFeatureSwitchManager.disable(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("cohoFirstHandOff", false))
      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())

      status(result) mustBe OK
      bodyOf(await(result)) mustBe "BooleanFeatureSwitch(cohoFirstHandOff,false)"
    }


    "return a first handoff feature state set to false as a default when we specify a non implemented feature name" in new Setup {
      val featureName = "Rubbish"
      val featureState = "coho"
      when(mockSCRSFeatureSwitches.apply(ArgumentMatchers.any[String])).thenReturn(None)
      val result = controller.handOffFeatureSwitch(featureName, featureState)(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }
}