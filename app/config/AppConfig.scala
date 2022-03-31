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

package config

import controllers.reg.routes
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URLEncoder
import javax.inject.Inject

class AppConfig @Inject()(val servicesConfig: ServicesConfig) {

  private def loadConfig(key: String) = servicesConfig.getString(key)

  private def loadOptionalConfig(key: String): Option[String] = try {
    Option(servicesConfig.getString(key))
  } catch {
    case _: Throwable => None
  }

  private lazy val contactFormServiceIdentifier = "SCRS"

  lazy val assetsPrefix = loadConfig(s"assets.url") + loadConfig(s"assets.version")
  lazy val reportAProblemPartialUrl = s"/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl = s"/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  lazy val govHostUrl = loadConfig(s"microservice.services.gov-uk.gov-host-domain")

  lazy val contactFrontendPartialBaseUrl = servicesConfig.baseUrl("contact-frontend")
  lazy val serviceId = contactFormServiceIdentifier

  lazy val corsRenewHost = loadOptionalConfig("cors-host.renew-session")

  lazy val timeoutInSeconds: String = loadConfig("microservice.timeoutInSeconds")
  lazy val timeoutDisplayLength: String = loadConfig("microservice.timeoutDisplayLength")

  lazy val commonFooterUrl = "https://www.tax.service.gov.uk/register-your-company/cookies-privacy-terms"
  lazy val helpFooterUrl = "https://www.gov.uk/help"

  val IR_CT = "IR-CT"
  val IR_PAYE = "IR-PAYE"
  val HMCE_VATDEC_ORG = "HMCE-VATDEC-ORG"
  val HMCE_VATVAR_ORG = "HMCE-VATVAR-ORG"
  val IR_SA_PART_ORG = "IR-SA-PART-ORG"
  val IR_SA_TRUST_ORG = "IR-SA-TRUST-ORG"
  val IR_SA = "IR-SA"

  lazy val SAEnrolments = List(IR_SA_PART_ORG, IR_SA_TRUST_ORG, IR_SA)
  lazy val restrictedEnrolments = List(IR_CT, IR_PAYE, HMCE_VATDEC_ORG, HMCE_VATVAR_ORG) ++ SAEnrolments

  lazy val self = servicesConfig.getConfString("comp-reg-frontend.url", throw new Exception("Could not find config for comp-reg-frontend url"))
  lazy val selfFull = servicesConfig.getConfString("comp-reg-frontend.fullurl", self)
  lazy val selfFullLegacy = servicesConfig.getConfString("comp-reg-frontend.legacyfullurl", selfFull)

  lazy val incorporationInfoUrl = servicesConfig.baseUrl("incorp-info")

  private def encodeUrl(url: String): String = URLEncoder.encode(url, "UTF-8")

  def accessibilityStatementUrl(pageUri: String) = controllers.routes.AccessibilityStatementController.show(pageUri).url

  lazy val accessibilityStatementUrl = selfFull + "/register-your-company" + "/accessibility-statement" + "?pageUri=%2F"

  lazy val contactHost = loadConfig("contact-frontend.host")

  def accessibilityReportUrl(userAction: String): String =
    s"$contactHost/contact/accessibility-unauthenticated?service=company-registration-frontend&userAction=${encodeUrl(userAction)}"

  lazy val basGatewayHost = try {
    servicesConfig.getString(s"microservice.services.bas-gateway-frontend.url")
  } catch {
    case _: Throwable => ""
  }
  lazy val loginCallback = try {
    servicesConfig.getString(s"microservice.services.auth.login-callback.url")
  }
  catch {
    case _: Throwable => ""
  }
  lazy val loginPath = try {
    servicesConfig.getString(s"microservice.services.bas-gateway-frontend.login_path")
  } catch {
    case _: Throwable => ""
  }
  lazy val logoutPath = try {
    servicesConfig.getString(s"microservice.services.bas-gateway-frontend.logout_path")
  } catch {
    case _: Throwable => ""
  }

  lazy val loginURL = s"$basGatewayHost$loginPath"
  lazy val logoutURL = s"$basGatewayHost$logoutPath"

  def prepopAddressUrl(registrationId: String): String =
    s"${servicesConfig.baseUrl("business-registration")}/business-registration/$registrationId/addresses"

  def continueURL(handOffID: Option[String], payload: Option[String]) = s"$loginCallback${routes.SignInOutController.postSignIn(None, handOffID, payload).url}"

  lazy val companyRegistrationUrl: String = servicesConfig.baseUrl("company-registration")

  lazy val taxYearStartDate: String = servicesConfig.getString("tax-year-start-date")

  lazy val currentPayeWeeklyThreshold: Int = servicesConfig.getInt("paye.weekly-threshold")
  lazy val currentPayeMonthlyThreshold: Int = servicesConfig.getInt("paye.monthly-threshold")
  lazy val currentPayeAnnualThreshold: Int = servicesConfig.getInt("paye.annual-threshold")
  lazy val oldPayeWeeklyThreshold: Int = servicesConfig.getInt("paye.old-weekly-threshold")
  lazy val oldPayeMonthlyThreshold: Int = servicesConfig.getInt("paye.old-monthly-threshold")
  lazy val oldPayeAnnualThreshold: Int = servicesConfig.getInt("paye.old-annual-threshold")

}
