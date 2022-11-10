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

import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time._
import javax.inject.Inject
import scala.util.Try


sealed trait FeatureSwitch {
  def name: String
  def enabled: Boolean
  def value: String
}



case class BooleanFeatureSwitch(name: String, enabled: Boolean) extends FeatureSwitch {
  override def value = ""
}

case class TimedFeatureSwitch(name: String, start: Option[Instant], end: Option[Instant], target: Instant) extends FeatureSwitch {

  override def enabled: Boolean = (start, end) match {
    case (Some(s), Some(e)) => !target.isBefore(s) && !target.isAfter(e)
    case (None, Some(e))    => !target.isAfter(e)
    case (Some(s), None)    => !target.isBefore(s)
    case (None, None)       => false
  }

  override def value = ""
}

case class ValueSetFeatureSwitch(name: String, setValue: String) extends FeatureSwitch {
  override def enabled = true
  override def value   = setValue
}

class FeatureSwitchManagerImpl @Inject()(val config: ServicesConfig) extends FeatureSwitchManager

trait FeatureSwitchManager {

  val config: ServicesConfig

  val DatesIntervalExtractor = """(\S+)_(\S+)""".r
  val UNSPECIFIED            = "X"

  private[utils] def getProperty(name: String): FeatureSwitch = {

    val value = sys.props.get(systemPropertyName(name)).fold(Try(config.getString(systemPropertyName(name))).toOption)(Some(_))

    value match {
      case Some("true")                                                => BooleanFeatureSwitch(name, enabled = true)
      case Some(DatesIntervalExtractor(start, end))                    => TimedFeatureSwitch(name, toDate(start), toDate(end), Instant.now())
      case Some("")                                                    => ValueSetFeatureSwitch(name, "time-clear")
      case Some(date) if date.matches(SCRSValidators.datePatternRegex) => ValueSetFeatureSwitch(name, date)
      case _                                                           => BooleanFeatureSwitch(name, enabled = false)
    }
  }

  private[utils] def setProperty(name: String, value: String): FeatureSwitch = {
    sys.props += ((systemPropertyName(name), value))
    getProperty(name)
  }

  private[utils] def toDate(text: String) : Option[Instant] = {
    text match {
      case UNSPECIFIED => None
      case _           => Some(Instant.parse(text))
    }
  }

  private[utils] def systemPropertyName(name: String) = s"feature.$name"

  def enable(fs: FeatureSwitch): FeatureSwitch  = setProperty(fs.name, "true")
  def disable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "false")

  def setSystemDate(fs: FeatureSwitch): FeatureSwitch   = setProperty(fs.name, fs.value)
  def clearSystemDate(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "")
  def apply(name: String, enabled: Boolean = false): FeatureSwitch = getProperty(name)
  def unapply(fs: FeatureSwitch): Option[(String, Boolean)]        = Some(fs.name -> fs.enabled)
}

class SCRSFeatureSwitchesImpl @Inject()(val featureSwitchManager: FeatureSwitchManager) extends SCRSFeatureSwitches {
  val COHO = "cohoFirstHandOff"
}

trait SCRSFeatureSwitches {

  val featureSwitchManager: FeatureSwitchManager
  val COHO: String
  val LEGACY_ENV: String = "legacyEnv"
  val takeoversKey: String = "takeovers"

  def cohoFirstHandOff          = featureSwitchManager.getProperty(COHO)
  def vat                       = featureSwitchManager.getProperty("vat")
  def legacyEnv                 = featureSwitchManager.getProperty(LEGACY_ENV)
  def systemDate                = featureSwitchManager.getProperty("system-date")
  def welshLanguage             = featureSwitchManager.getProperty("toggle-welsh")

  def apply(name: String): Option[FeatureSwitch] = name match {
    case COHO                        => Some(cohoFirstHandOff)
    case "vat"                       => Some(vat)
    case LEGACY_ENV                  => Some(legacyEnv)
    case "system-date"               => Some(systemDate)
    case "toggle-welsh"              => Some(welshLanguage)
    case _                           => None
  }
}
