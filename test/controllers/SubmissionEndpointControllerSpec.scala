/*
 * Copyright 2017 HM Revenue & Customs
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
import config.FrontendAuthConnector
import connectors.S4LConnector
import controllers.test.SubmissionEndpointController
import fixtures.SCRSFixtures
import helpers.SCRSSpec
import play.api.test.Helpers._
import models._
import play.api.libs.json.Json
import uk.gov.hmrc.http.cache.client.CacheMap
import org.mockito.Mockito._
import org.mockito.Matchers
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

/**
  * Created by crispy on 11/07/16.
  */
class SubmissionEndpointControllerSpec extends SCRSSpec with SCRSFixtures with WithFakeApplication {

  val cacheMap = CacheMap("", Map("" -> Json.toJson("")))

  val userIds = UserIDs("testInternal","testExternal")

  class Setup {
    val controller = new SubmissionEndpointController {
      val authConnector = mockAuthConnector
      val s4LConnector = mockS4LConnector
    }

    implicit val user = AuthBuilder.createTestUser
    implicit val hc = HeaderCarrier()

  }

  "SubmissionEndpointController" should {
    "use the correct AuthConnector" in {
      SubmissionEndpointController.authConnector shouldBe FrontendAuthConnector
    }
    "use the correct S4LConnector" in {
      SubmissionEndpointController.s4LConnector shouldBe S4LConnector
    }
  }

  "getAllS4LEntries" should {
    "Return a 200" in new Setup {

      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userIds))

      mockS4LFetchAndGet[SubmissionModel]("SubmissionData", Some(validSubmissionModel))

      AuthBuilder.showWithAuthorisedUser(controller.getAllS4LEntries, mockAuthConnector){

        result =>
          status(result) shouldBe OK
      }
    }
    "Also return a 200 when handback contains no payload" in new Setup {
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(userIds))

      mockS4LFetchAndGet[SubmissionModel]("SubmissionData", None)

      AuthBuilder.showWithAuthorisedUser(controller.getAllS4LEntries, mockAuthConnector){

        result =>
          status(result) shouldBe OK
      }
    }
  }
}
