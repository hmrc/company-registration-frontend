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

package utils

import java.time.Instant
import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class FeatureSwitchSpec extends SCRSSpec with MockitoSugar {

  override def beforeEach() {
    resetMocks()
    System.clearProperty("feature.test")
    System.clearProperty("feature.cohoFirstHandOff")
  }

  override def afterAll(): Unit = {
    super.afterAll()
    System.clearProperty("feature.system-date")
  }

  class SetupForFeatureManager {
    val fMan = new FeatureSwitchManager {
      override val config: ServicesConfig = mockServicesConfig
    }
  }

  "apply" should {

    "return a constructed BooleanFeatureSwitch if the set system property is a boolean" in new SetupForFeatureManager {
      System.setProperty("feature.test", "true")

      fMan.apply("test") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "create an instance of BooleanFeatureSwitch which inherits FeatureSwitch" in new SetupForFeatureManager {
      when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))
      fMan.apply("test") mustBe a[BooleanFeatureSwitch]
    }

    "create an instance of TimedFeatureSwitch which inherits FeatureSwitch" in new SetupForFeatureManager {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      fMan.apply("test") mustBe a[FeatureSwitch]
      fMan.apply("test") mustBe a[TimedFeatureSwitch]
    }

    "return an enabled TimedFeatureSwitch when only the end datetime is specified and is in the future" in new SetupForFeatureManager {
      System.setProperty("feature.test", "X_9999-05-08T14:30:00Z")

      fMan.apply("test") mustBe a[TimedFeatureSwitch]
      fMan.apply("test").enabled mustBe true
    }

    "return a disabled TimedFeatureSwitch when only the end datetime is specified and is in the past" in new SetupForFeatureManager {
      System.setProperty("feature.test", "X_2000-05-08T14:30:00Z")

      fMan.apply("test") mustBe a[TimedFeatureSwitch]
      fMan.apply("test").enabled mustBe false
    }

    "return an enabled TimedFeatureSwitch when only the start datetime is specified and is in the past" in new SetupForFeatureManager {
      System.setProperty("feature.test", "2000-05-05T14:30:00Z_X")

      fMan.apply("test") mustBe a[TimedFeatureSwitch]
      fMan.apply("test").enabled mustBe true
    }

    "return a disabled TimedFeatureSwitch when neither date is specified" in new SetupForFeatureManager {
      System.setProperty("feature.test", "X_X")

      fMan.apply("test").enabled mustBe false
    }
  }

  "unapply" should {

    "deconstruct a given FeatureSwitch into it's name and a false enabled value if undefined as a system property" in new SetupForFeatureManager {
      when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))
      val fs = fMan("test")

      fMan.unapply(fs) mustBe Some("test" -> false)
    }

    "deconstruct a given FeatureSwitch into its name and true if defined as true as a system property" in new SetupForFeatureManager {
      System.setProperty("feature.test", "true")
      val fs = fMan("test")

      fMan.unapply(fs) mustBe Some("test" -> true)
    }

    "deconstruct a given FeatureSwitch into its name and false if defined as false as a system property" in new SetupForFeatureManager {
      System.setProperty("feature.test", "false")
      val fs = fMan("test")

      fMan.unapply(fs) mustBe Some("test" -> false)
    }

    "deconstruct a given TimedFeatureSwitch into its name and enabled flag if defined as a system property" in new SetupForFeatureManager {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")
      val fs = fMan("test")

      fMan.unapply(fs) mustBe Some("test" -> false)
    }
  }

  "getProperty" should {

    "return a disabled feature switch if the system property is undefined AND no app-config entry is defined" in new SetupForFeatureManager {

      when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))

      fMan.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return an enabled feature switch if the system property is undefined BUT app-config entry is defined and set to true" in new SetupForFeatureManager {

      when(mockServicesConfig.getString("feature.test")).thenReturn("true")

      fMan.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return an enabled feature switch if the system property is defined as 'true'" in new SetupForFeatureManager {
      System.setProperty("feature.test", "true")

      fMan.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return an enabled feature switch if the system property is defined as 'false'" in new SetupForFeatureManager {
      System.setProperty("feature.test", "false")

      fMan.getProperty("test") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a TimedFeatureSwitch when the set system property is a date" in new SetupForFeatureManager {
      System.setProperty("feature.test", "2016-05-05T14:30:00Z_2016-05-08T14:30:00Z")

      fMan.getProperty("test") mustBe a[TimedFeatureSwitch]
    }
  }

  "systemPropertyName" should {

    "append feature. to the supplied string'" in new SetupForFeatureManager {
      fMan.systemPropertyName("test") mustBe "feature.test"
    }
  }

  "setProperty" should {

    "return a feature switch (testKey, false) when supplied with (testKey, testValue)" in new SetupForFeatureManager {
      fMan.setProperty("test", "testValue") mustBe BooleanFeatureSwitch("test", enabled = false)
    }

    "return a feature switch (testKey, true) when supplied with (testKey, true)" in new SetupForFeatureManager {
      fMan.setProperty("test", "true") mustBe BooleanFeatureSwitch("test", enabled = true)
    }

    "return ValueSetFeatureSwitch when supplied system-date and 2018-01-01" in new SetupForFeatureManager {
      fMan.setProperty("system-date", "2018-01-01") mustBe ValueSetFeatureSwitch("system-date", "2018-01-01")
    }
  }

  "enable" should {
    "set the value for the supplied key to 'true'" in new SetupForFeatureManager {

      when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))
      val fs = fMan("test")
      System.setProperty("feature.test", "false")

      fMan.enable(fs) mustBe BooleanFeatureSwitch("test", enabled = true)
    }
  }

  "disable" should {
    "set the value for the supplied key to 'false'" in new SetupForFeatureManager {

      when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))
      val fs = fMan("test")
      System.setProperty("feature.test", "true")

      fMan.disable(fs) mustBe BooleanFeatureSwitch("test", enabled = false)
    }
  }

  "dynamic toggling should be supported" in new SetupForFeatureManager {
    when(mockServicesConfig.getString("feature.test")).thenThrow(new RuntimeException("NotFound"))
    val fs = fMan("test")

    fMan.disable(fs).enabled mustBe false
    fMan.enable(fs).enabled mustBe true
  }

  "TimedFeatureSwitch" should {

    val START = "2000-01-23T14:00:00.00Z"
    val END = "2000-01-23T15:30:00.00Z"
    val startDateTime = Some(Instant.parse(START))
    val endDatetime = Some(Instant.parse(END))

    "be enabled when within the specified time range" in new SetupForFeatureManager {
      val now = Instant.parse("2000-01-23T14:30:00.00Z")

      TimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
    }

    "be enabled when current time is equal to the start time" in new SetupForFeatureManager {
      val now = Instant.parse(START)

      TimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
    }

    "be enabled when current time is equal to the end time" in new SetupForFeatureManager {
      val now = Instant.parse(END)

      TimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe true
    }

    "be disabled when current time is outside the specified time range" in new SetupForFeatureManager {
      val now = Instant.parse("1900-01-23T12:00:00Z")

      TimedFeatureSwitch("test", startDateTime, endDatetime, now).enabled mustBe false
    }

    "be disabled when current time is in the future of the specified time range with an unspecified start" in new SetupForFeatureManager {
      val now = Instant.parse("2100-01-23T12:00:00Z")

      TimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe false
    }

    "be enabled when current time is in the past of the specified time range with an unspecified start" in new SetupForFeatureManager {
      val now = Instant.parse("1900-01-23T12:00:00Z")

      TimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
    }

    "be enabled when current time is in the range of the specified time range with an unspecified start" in new SetupForFeatureManager {
      val now = Instant.parse("2000-01-23T14:30:00.00Z")

      TimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
    }

    "be enabled when current time is in the future of the specified time range with an unspecified end" in new SetupForFeatureManager {
      val now = Instant.parse("2100-01-23T12:00:00Z")

      TimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe true
    }

    "be disabled when current time is in the past of the specified time range with an unspecified end" in new SetupForFeatureManager {
      val now = Instant.parse("1900-01-23T12:00:00Z")

      TimedFeatureSwitch("test", startDateTime, None, now).enabled mustBe false
    }

    "be enabled when current time is in the range of the specified time range with an unspecified end" in new SetupForFeatureManager {
      val now = Instant.parse("2000-01-23T14:30:00.00Z")

      TimedFeatureSwitch("test", None, endDatetime, now).enabled mustBe true
    }
  }

  class Setup {
    val fMan = new FeatureSwitchManager {
      override val config: ServicesConfig = mockServicesConfig
    }
    System.setProperty("feature.sausages", "")
    val scrsFeatureSwitch = new SCRSFeatureSwitches {
      override val COHO: String = "sausages"
      override val featureSwitchManager: FeatureSwitchManager = mockFeatureSwitchManager
    }
  }

  "SCRSFeatureSwitches" should {
    "return a disabled feature when the associated system property doesn't exist" in new Setup {
      when(mockFeatureSwitchManager.getProperty(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("sausages§", false))
      scrsFeatureSwitch.cohoFirstHandOff.enabled mustBe false
    }

    "return an enabled feature when the associated system property is true" in new Setup {
      when(mockFeatureSwitchManager.getProperty(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("foobarFeatureFLUFF", true))
      fMan.enable(scrsFeatureSwitch.cohoFirstHandOff)

      scrsFeatureSwitch.cohoFirstHandOff.enabled mustBe true
    }

    "return a disabled welsh feature when the associated system property is false" in new Setup {
      when(mockFeatureSwitchManager.getProperty(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("toggle-welsh", false))
      scrsFeatureSwitch.welshLanguage.enabled mustBe false
    }

    "return an enabled welsh feature when the associated system property is true" in new Setup {
      when(mockFeatureSwitchManager.getProperty(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("toggle-welsh", true))
      fMan.enable(scrsFeatureSwitch.welshLanguage)

      scrsFeatureSwitch.welshLanguage.enabled mustBe true
    }

    "return a cohoFirstHandOff SCRS feature if it exists" in new Setup {
      when(mockFeatureSwitchManager.getProperty(ArgumentMatchers.any())).thenReturn(BooleanFeatureSwitch("cohoFirstHandOff", true))
      System.setProperty("feature.sages", "true")

      scrsFeatureSwitch("sausages") mustBe Some(BooleanFeatureSwitch("cohoFirstHandOff", true))
    }

    "return an empty option if the cohoFirstHandOff system property doesn't exist when using the apply function" in new Setup {
      scrsFeatureSwitch("walls") mustBe None
    }
  }
}