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

package config

import java.util.Base64

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

trait AppConfig {
  val assetsPrefix: String
  val analyticsToken: String
  val analyticsHost: String
  val piwikURL: Option[String]
  val analyticsAutoLink: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String

  val contactFrontendPartialBaseUrl : String
  val serviceId : String

  val corsRenewHost: Option[String]

  val timeoutInSeconds: String
  val timeoutDisplayLength: String
}

object FrontendAppConfig extends AppConfig with ServicesConfig {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))
  private def loadOptionalConfig(key: String) = configuration.getString(key)

  private val contactFormServiceIdentifier = "SCRS"

  override lazy val assetsPrefix = loadConfig(s"assets.url") + loadConfig(s"assets.version")
  override lazy val analyticsToken = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"google-analytics.host")
  override lazy val piwikURL = loadOptionalConfig(s"piwik.url")
  override lazy val analyticsAutoLink = loadConfig(s"google-analytics.autolink")
  override lazy val reportAProblemPartialUrl = s"/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"

  override lazy val contactFrontendPartialBaseUrl = baseUrl("contact-frontend")
  override lazy val serviceId = contactFormServiceIdentifier

  override lazy val corsRenewHost = loadOptionalConfig("cors-host.renew-session")

  override val timeoutInSeconds: String = loadConfig("microservice.timeoutInSeconds")
  override val timeoutDisplayLength: String = loadConfig("microservice.timeoutDisplayLength")

  private def whitelistConfig(key: String): Seq[String] = Some(new String(Base64.getDecoder
    .decode(configuration.getString(key).getOrElse("")), "UTF-8"))
    .map(_.split(",")).getOrElse(Array.empty).toSeq

  lazy val whitelist = whitelistConfig("whitelist")
  lazy val whitelistExcluded = whitelistConfig("whitelist-excluded")

  private val IR_CT           = "IR-CT"
  private val IR_PAYE         = "IR-PAYE"
  private val HMCE_VATDEC_ORG = "HMCE-VATDEC-ORG"
  private val HMCE_VATVAR_ORG = "HMRC-VATVAR-ORG"
  private val IR_SA_PART_ORG  = "IR-SA-PART-ORG"
  private val IR_SA_TRUST_ORG = "IR-SA-TRUST-ORG"
  private val IR_SA           = "IR-SA"

  val SAEnrolments         = List(IR_SA_PART_ORG, IR_SA_TRUST_ORG, IR_SA)
  val restrictedEnrolments = List(IR_CT, IR_PAYE, HMCE_VATDEC_ORG, HMCE_VATVAR_ORG) ++ SAEnrolments
}
