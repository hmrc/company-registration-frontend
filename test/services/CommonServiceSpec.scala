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

package services

import helpers.SCRSSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.libs.json.{JsValue, Json}
import utils.{SCRSException, SCRSExceptions}

import java.time.LocalDate
import scala.concurrent.Future

class CommonServiceSpec extends SCRSSpec {

  trait Setup {
    val service = new CommonService with SCRSExceptions {
      override val sessionCacheService = mockSessionCacheService
    }
  }


  "fetchRegistration" should {
    "return a registrationID if one exists in keystore" in new Setup {
      mockSessionCacheGet[String]("registrationID", Some("12345"))

      await(service.fetchRegistrationID) mustBe "12345"
    }

    "throw a Not Found Exception when a registrationID does not exist in keystore" in new Setup {
      mockSessionCacheGet[String]("registrationID", None)

      val exception: SCRSException = intercept[SCRSException]{
        await(service.fetchRegistrationID)
      }

      exception.message mustBe "Could not find a company details record - suspected direct routing before a registration ID could be created"
    }
  }

  "cacheRegistrationID" should {
    "cache the passed registrationID in session store" in new Setup {
      mockSessionCacheSave[String]("registrationID", "12345")

      await(service.cacheRegistrationID("12345")) mustBe "12345"
    }
  }

  "updateLastActionTimestamp" should {
    val jsValueTime = Json.toJson(LocalDate.now())

    "cache the passed timestamp in session store" in new Setup {
      when(mockSessionCacheService.save[JsValue](ArgumentMatchers.any(), ArgumentMatchers.any())
        (ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(Future.successful(jsValueTime))

      await(service.updateLastActionTimestamp()) mustBe jsValueTime
    }
  }
}
