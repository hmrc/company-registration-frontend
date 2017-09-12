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

package utils

import fixtures.JweFixture
import helpers.SCRSSpec
import org.jose4j.jwe.{ContentEncryptionAlgorithmIdentifiers => CEAI}
import org.jose4j.jwe.{KeyManagementAlgorithmIdentifiers => KMAI}
import org.jose4j.jwe.{JsonWebEncryption => JWE}
import org.jose4j.keys.AesKey
import play.api.libs.json.{JsObject, JsValue, Json}

import scala.util.{Failure, Success}

class JWECheckDecryption extends SCRSSpec with JweFixture {

  class Setup {
    def jweOverrideKey(overrideKey: String): JweEncryptor with JweDecryptor = new JweEncryptor with JweDecryptor {
      override protected val key = overrideKey
    }
  }

  "JWE Decryptor when decrypting a payload" should {

    "pull back a consistent payload after encrypt / decrypt for DIRECT header" in new Setup {

      val testJwe = jweOverrideKey("12345678901234567890123457890123")

      val payload = "eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..QhASSFMKteeuTg8IVvjVRA.0DNqV8CAvixFFz13f9sP5g.VcM2dH86YeIhJjXUj1AA2A"

      testJwe.decrypt[JsObject](payload) match {
        case Success(s) => s shouldBe Json.obj("foo" -> "bar")
        case unexpected => fail(s"Test failed with unexpected result - ${unexpected}")
      }
    }
  }
}
