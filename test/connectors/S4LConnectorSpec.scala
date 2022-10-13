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

package connectors

import helpers.UnitSpec
import models.AccountingDatesModel
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class S4LConnectorSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite {

  override lazy val app = new GuiceApplicationBuilder()
    .configure(
      "Test.microservices.services.cachable.short-lived.cache.host" -> "test-only",
      "Test.microservices.services.cachable.short-lived.cache.port" -> 99999,
      "Test.microservices.services.cachable.short-lived.cache.domain" -> "save4later"
    )
    .build()

  val mockShortLivedCache = mock[ShortLivedCache]

  object S4LConnectorTest extends S4LConnector {
    override val shortCache = mockShortLivedCache
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val dateModel = AccountingDatesModel("", Some("1"), Some("1"), Some("2019"))
  val cacheMap = CacheMap("", Map("" -> Json.toJson(dateModel)))

  "Fetching from save4later" should {
    "return the correct model" in {
      val model = AccountingDatesModel("", Some("1"), Some("1"), Some("2019"))

      when(mockShortLivedCache.fetchAndGetEntry[AccountingDatesModel](ArgumentMatchers.anyString(), ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Option(model)))

      val result = S4LConnectorTest.fetchAndGet[AccountingDatesModel]("", "")
      await(result) mustBe Some(model)
    }
  }

  "Saving a model into save4later" should {
    "save the model" in {
      val model = AccountingDatesModel("", Some("1"), Some("1"), Some("2019"))
      val returnCacheMap = CacheMap("", Map("" -> Json.toJson(model)))

      when(mockShortLivedCache.cache[AccountingDatesModel](ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(returnCacheMap))

      val result = S4LConnectorTest.saveForm[AccountingDatesModel]("", "", model)
      await(result) mustBe returnCacheMap
    }
  }

  "clearing an entry using save4later" should {
    "clear the entry given the user id" in {
      when(mockShortLivedCache.remove(ArgumentMatchers.anyString())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(HttpResponse(OK, "")))

      val result = S4LConnectorTest.clear("test")
      await(result).status mustBe HttpResponse(OK, "").status
    }
  }

  "fetchAll" should {
    "fetch all entries in S4L" in {
      when(mockShortLivedCache.fetch(ArgumentMatchers.any())(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(Some(cacheMap)))

      val result = S4LConnectorTest.fetchAll("testUserId")
      await(result).get mustBe cacheMap
    }
  }
}
