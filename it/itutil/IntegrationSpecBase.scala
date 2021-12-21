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

package itutil

import org.scalatest._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}
import utils.{FeatureSwitch, FeatureSwitchManager, SCRSFeatureSwitches}
import WiremockHelper._
import play.api.libs.ws.WSResponse
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}


trait IntegrationSpecBase extends WordSpec
  with GivenWhenThen
  with GuiceOneServerPerSuite
  with ScalaFutures
  with Matchers
  with WiremockHelper
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with FutureAwaits
  with DefaultAwaitTimeout
  with IntegrationPatience {

  val testkey = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Test))
    .configure(config)
    .build

  def config: Map[String, String] = Map(
    "play.filters.csrf.header.bypassHeaders.X-Requested-With" -> "*",
    "play.filters.csrf.header.bypassHeaders.Csrf-Token" -> "nocheck",
    "auditing.consumer.baseUri.host" -> s"$wiremockHost",
    "auditing.consumer.baseUri.port" -> s"$wiremockPort",
    "microservice.services.gg-reg-fe.url" -> s"wibble",
    "microservice.services.cachable.session-cache.host" -> s"$wiremockHost",
    "microservice.services.cachable.session-cache.port" -> s"$wiremockPort",
    "microservice.services.cachable.session-cache.domain" -> "keystore",
    "microservice.services.auth.host" -> s"$wiremockHost",
    "microservice.services.auth.port" -> s"$wiremockPort",
    "microservice.services.company-registration.host" -> s"$wiremockHost",
    "microservice.services.company-registration.port" -> s"$wiremockPort",
    "microservice.services.incorp-info.host" -> s"$wiremockHost",
    "microservice.services.incorp-info.port" -> s"$wiremockPort",
    "microservice.services.address-lookup-frontend.host" -> s"$wiremockHost",
    "microservice.services.address-lookup-frontend.port" -> s"$wiremockPort",
    "microservice.services.business-registration.host" -> s"$wiremockHost",
    "microservice.services.business-registration.port" -> s"$wiremockPort",
    "microservice.timeoutInSeconds" -> "999999",
    "microservice.services.email-vs.sendVerificationEmailURL" -> s"$url/sendVerificationEmailURL",
    "microservice.services.email-vs.checkVerifiedEmailURL" -> s"$url/checkVerifiedEmailURL",
    "microservice.services.paye-registration.host" -> s"$wiremockHost",
    "microservice.services.paye-registration.port" -> s"$wiremockPort",
    "microservice.services.vat-registration.host" -> s"$wiremockHost",
    "microservice.services.vat-registration.port" -> s"$wiremockPort",
    "microservice.services.paye-registration-www.url-prefix" -> "paye-url",
    "microservice.services.paye-registration-www.start-url" -> "/start",
    "microservice.services.vat-registration-www.url-prefix" -> "vat-url",
    "microservice.services.vat-registration-www.start-url" -> "/start",
    "auditing.enabled" -> s"true",
    "auditing.traceRequests" -> s"true",
    "microservice.services.JWE.key" -> testkey
  )

  def setupFeatures(cohoFirstHandOff: Boolean = false,
                    businessActivitiesHandOff: Boolean = false,
                    paye: Boolean = false,
                    vat: Boolean = false,
                    signPosting: Boolean = false,
                    takeovers: Boolean = false
                   ): FeatureSwitch = {
    def enableFeature(fs: FeatureSwitch, enabled: Boolean): FeatureSwitch = {
      if (enabled) {
        app.injector.instanceOf[FeatureSwitchManager].enable(fs)
      } else {
        app.injector.instanceOf[FeatureSwitchManager].disable(fs)
      }
    }

    enableFeature(app.injector.instanceOf[SCRSFeatureSwitches].cohoFirstHandOff, cohoFirstHandOff)
    enableFeature(app.injector.instanceOf[SCRSFeatureSwitches].businessActivitiesHandOff, businessActivitiesHandOff)
    enableFeature(app.injector.instanceOf[SCRSFeatureSwitches].paye, paye)
    enableFeature(app.injector.instanceOf[SCRSFeatureSwitches].vat, vat)
    enableFeature(app.injector.instanceOf[SCRSFeatureSwitches].takeovers, takeovers)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    resetWiremock()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    startWiremock()
  }

  override def afterAll(): Unit = {
    stopWiremock()
    super.afterAll()
  }

 implicit class ResponseUtils(wsResponse: WSResponse) {
   lazy val redirectLocation: Option[String] = wsResponse.header("Location")
 }
}
