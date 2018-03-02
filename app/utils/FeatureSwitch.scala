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

package utils

import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json


sealed trait FeatureSwitch {
  def name: String
  def enabled: Boolean
  def value: String
}

case class BooleanFeatureSwitch(name: String, enabled: Boolean) extends FeatureSwitch {
  override def value = ""
}

case class TimedFeatureSwitch(name: String, start: Option[DateTime], end: Option[DateTime], target: DateTime) extends FeatureSwitch {

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

object FeatureSwitch {

  val DatesIntervalExtractor = """(\S+)_(\S+)""".r
  val UNSPECIFIED            = "X"
  val dateFormat             = ISODateTimeFormat.dateTimeNoMillis()

  private[utils] def getProperty(name: String): FeatureSwitch = {
    val value = sys.props.get(systemPropertyName(name))

    value match {
      case Some("true")                                                => BooleanFeatureSwitch(name, enabled = true)
      case Some(DatesIntervalExtractor(start, end))                    => TimedFeatureSwitch(name, toDate(start), toDate(end), DateTime.now(DateTimeZone.UTC))
      case Some("")                                                    => ValueSetFeatureSwitch(name, "time-clear")
      case Some(date) if date.matches(SCRSValidators.datePatternRegex) => ValueSetFeatureSwitch(name, date)
      case _                                                           => BooleanFeatureSwitch(name, enabled = false)
    }
  }

  private[utils] def setProperty(name: String, value: String): FeatureSwitch = {
    sys.props += ((systemPropertyName(name), value))
    getProperty(name)
  }

  private[utils] def toDate(text: String) : Option[DateTime] = {
    text match {
      case UNSPECIFIED => None
      case _           => Some(dateFormat.parseDateTime(text))
    }
  }

  private[utils] def systemPropertyName(name: String) = s"feature.$name"

  def enable(fs: FeatureSwitch): FeatureSwitch  = setProperty(fs.name, "true")
  def disable(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "false")

  def setSystemDate(fs: FeatureSwitch): FeatureSwitch   = setProperty(fs.name, fs.value)
  def clearSystemDate(fs: FeatureSwitch): FeatureSwitch = setProperty(fs.name, "")

  def apply(name: String, enabled: Boolean = false): FeatureSwitch = getProperty(name)
  def unapply(fs: FeatureSwitch): Option[(String, Boolean)]        = Some(fs.name -> fs.enabled)

  implicit val formats = Json.format[FeatureSwitch]
}

object SCRSFeatureSwitches extends SCRSFeatureSwitches {
  val COHO = "cohoFirstHandOff"
}

trait SCRSFeatureSwitches {

  val COHO: String
  val LEGACY_ENV: String = "legacyEnv"

  def cohoFirstHandOff          = FeatureSwitch.getProperty(COHO)
  def businessActivitiesHandOff = FeatureSwitch.getProperty("businessActivitiesHandOff")
  def paye                      = FeatureSwitch.getProperty("paye")
  def vat                       = FeatureSwitch.getProperty("vat")
  def legacyEnv                 = FeatureSwitch.getProperty(LEGACY_ENV)
  def contactUs                 = FeatureSwitch.getProperty("contactUs")
  def systemDate                = FeatureSwitch.getProperty("system-date")
  def signPosting               = FeatureSwitch.getProperty("signPosting")

  def apply(name: String): Option[FeatureSwitch] = name match {
    case COHO                        => Some(cohoFirstHandOff)
    case "businessActivitiesHandOff" => Some(businessActivitiesHandOff)
    case "paye"                      => Some(paye)
    case "vat"                       => Some(vat)
    case LEGACY_ENV                  => Some(legacyEnv)
    case "contactUs"                 => Some(contactUs)
    case "system-date"               => Some(systemDate)
    case "signPosting"               => Some(signPosting)
    case _                           => None
  }
}
