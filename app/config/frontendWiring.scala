/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Configuration
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.cache.client.SessionCache
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.play.audit.http.HttpAuditing

import javax.inject.Inject

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = NoneRequired
}

class FrontendAuthConnector @Inject() (appConfig: AppConfig, val httpClientV2: HttpClientV2) extends PlayAuthConnector {
  override lazy val serviceUrl = appConfig.servicesConfig.baseUrl("auth")
}

class CryptoInitialiserImpl @Inject() (val applicationCrypto: ApplicationCrypto) extends CryptoInitialiser

trait CryptoInitialiser {
  lazy val cryptoInstance = applicationCrypto
  val applicationCrypto: ApplicationCrypto
}

class SCRSSessionCache @Inject() (appConfig: AppConfig, val httpClientV2: HttpClientV2, val appNameConfiguration: Configuration)
    extends SessionCache {
  override lazy val defaultSource = appConfig.servicesConfig.getString("appName")
  override lazy val baseUri       = appConfig.servicesConfig.baseUrl("cachable.session-cache")
  override lazy val domain = appConfig.servicesConfig.getConfString(
    "cachable.session-cache.domain",
    throw new Exception(s"Could not find config 'cachable.session-cache.domain'"))
}
