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

package services

import java.time.LocalDate

import helpers.SCRSSpec
import org.mockito.Matchers
import org.mockito.Mockito.when
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import utils.{SCRSException, SCRSExceptions}

import scala.concurrent.Future

class CommonServiceSpec extends SCRSSpec {

  trait Setup {
    val service = new CommonService with SCRSExceptions {
      override val keystoreConnector = mockKeystoreConnector
    }
  }

  val cacheMap = CacheMap("registrationID", Map("registrationID" -> Json.toJson("12345")))


  "fetchRegistration" should {
    "return a registrationID if one exists in keystore" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", Some("12345"))

      await(service.fetchRegistrationID) shouldBe "12345"
    }

    "throw a Not Found Exception when a registrationID does not exist in keystore" in new Setup {
      mockKeystoreFetchAndGet[String]("registrationID", None)

      val exception = intercept[SCRSException]{
        await(service.fetchRegistrationID)
      }

      exception.message shouldBe "Could not find a company details record - suspected direct routing before a registration ID could be created"
    }
  }

  "cacheRegistrationID" should {
    "cache the passed registrationID in keystore" in new Setup {
      mockKeystoreCache("registrationID", "12345", cacheMap)

      await(service.cacheRegistrationID("12345")) shouldBe cacheMap
    }
  }

  "updateLastActionTimestamp" should {
    val timestampCacheMap = CacheMap("lastActionTimestamp", Map("lastActionTimestamp" -> Json.toJson(LocalDate.now())))

    "cache the passed timestamp in keystore" in new Setup {
      when(mockKeystoreConnector.cache(Matchers.contains("lastActionTimestamp"), Matchers.any[LocalDate]())(Matchers.any[HeaderCarrier](), Matchers.any()))
        .thenReturn(Future.successful(timestampCacheMap))

      await(service.updateLastActionTimestamp()) shouldBe timestampCacheMap
    }
  }
}
