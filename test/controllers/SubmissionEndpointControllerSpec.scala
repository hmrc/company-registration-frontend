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

package controllers

import builders.AuthBuilder
import config.FrontendAppConfig
import controllers.test.SubmissionEndpointController
import fixtures.SCRSFixtures
import helpers.SCRSSpec
import models._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.MessagesControllerComponents
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.reg.{SubmissionEndpoint => SubmissionEndpointView}
import scala.concurrent.ExecutionContext.Implicits.global

class SubmissionEndpointControllerSpec extends SCRSSpec with SCRSFixtures with GuiceOneAppPerSuite with AuthBuilder {

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))
  lazy val appConfig: FrontendAppConfig = app.injector.instanceOf[FrontendAppConfig]
  lazy val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  lazy val mockSubmissionEndpointView = app.injector.instanceOf[SubmissionEndpointView]
  class Setup {
    val controller = new SubmissionEndpointController(
      mockAuthConnector,
      mockS4LConnector,
      mockMcc,
      mockSubmissionEndpointView
    )
    (
      appConfig,
      global
    )
  }

  val internalID = Some("internalID")

  "getAllS4LEntries" should {
    "Return a 200" in new Setup {

      mockS4LFetchAndGet[SubmissionModel]("SubmissionData", Some(validSubmissionModel))

      showWithAuthorisedUserRetrieval(controller.getAllS4LEntries, internalID) {

        result =>
          status(result) shouldBe OK
      }
    }
    "Also return a 200 when handback contains no payload" in new Setup {

      mockS4LFetchAndGet[SubmissionModel]("SubmissionData", None)

      showWithAuthorisedUserRetrieval(controller.getAllS4LEntries, internalID) {

        result =>
          status(result) shouldBe OK
      }
    }
  }
}