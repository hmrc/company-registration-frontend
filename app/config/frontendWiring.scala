/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.ActorSystem
import com.typesafe.config.Config
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.WSClient
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.http.hooks.HttpHooks
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.ws._

trait Hooks extends HttpHooks with HttpAuditing {
  override val hooks = NoneRequired
}

class WSHttpImpl @Inject()(appConfig: FrontendAppConfig, config:Configuration, val auditConnector: AuditConnector, val actorSystem: ActorSystem, val wsClient: WSClient) extends WSHttp {

  override val appName = appConfig.servicesConfig.getString("appName")
  override protected def configuration: Option[Config] = Option(config.underlying)
  protected def appNameConfiguration: Configuration = config
}

trait WSHttp extends
  WSGet with HttpGet with
  WSPut with HttpPut with
  WSPost with HttpPost with
  WSDelete with HttpDelete with
  WSPatch with HttpPatch with Hooks

class FrontendAuthConnector @Inject()(appConfig: FrontendAppConfig, val http: WSHttp) extends PlayAuthConnector {
  override lazy val serviceUrl = appConfig.servicesConfig.baseUrl("auth")
}

class SCRSShortLivedHttpCaching @Inject()(val wSHttp: WSHttp, appConfig: FrontendAppConfig, val appNameConfiguration:Configuration) extends ShortLivedHttpCaching {
  override lazy val http = wSHttp
  override lazy val defaultSource = appConfig.servicesConfig.getString("appName")
  override lazy val baseUri = appConfig.servicesConfig.baseUrl("cachable.short-lived-cache")
  override lazy val domain = appConfig.servicesConfig.getConfString("cachable.short-lived-cache.domain",
    throw new Exception(s"Could not find config 'cachable.short-lived-cache.domain'"))
}

class SCRSShortLivedCache @Inject()(cryptoInitialiser: CryptoInitialiser, val shortLiveCache: ShortLivedHttpCaching) extends ShortLivedCache {
  override implicit lazy val crypto = cryptoInitialiser.cryptoInstance.JsonCrypto
}
class CryptoInitialiserImpl @Inject()(val applicationCrypto: ApplicationCrypto) extends CryptoInitialiser

trait CryptoInitialiser {
  val applicationCrypto: ApplicationCrypto
  lazy val cryptoInstance = applicationCrypto
}

class SCRSSessionCache @Inject()(appConfig: FrontendAppConfig, val wSHttp: WSHttp, val appNameConfiguration: Configuration) extends SessionCache {
  override lazy val http = wSHttp
  override lazy val defaultSource = appConfig.servicesConfig.getString("appName")
  override lazy val baseUri = appConfig.servicesConfig.baseUrl("cachable.session-cache")
  override lazy val domain = appConfig.servicesConfig.getConfString("cachable.session-cache.domain",
    throw new Exception(s"Could not find config 'cachable.session-cache.domain'"))
}