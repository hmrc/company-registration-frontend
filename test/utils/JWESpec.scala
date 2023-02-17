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

package utils

import fixtures.JweFixture
import helpers.SCRSSpec
import org.jose4j.jwe.{ContentEncryptionAlgorithmIdentifiers => CEAI, JsonWebEncryption => JWE, KeyManagementAlgorithmIdentifiers => KMAI}
import org.jose4j.keys.AesKey
import play.api.libs.json.{JsValue, Json}

import scala.util.{Failure, Success}

class JWESpec extends SCRSSpec with JweFixture {

  class Setup {
    def jweOverrideKey(overrideKey: String) = new JweCommon {
      override val key = overrideKey
    }
    val jweWrongKey = new JweCommon {
      override val key: String = "invalidKey"
    }
  }

  "JWE Decryptor unpack" should {
    "produce a valid Wibble case class from sample JSON" in new Setup {
      val wibble = JweWithTestKey.unpack[JweTestModel](fooBarJson)
      wibble mustBe Success(JweTestModel("foo", "bar"))
    }

    "return a payload error when invalid Json is validated" in new Setup {

      val invalid = Json.toJson(JweTestModel("foo", "bar"))
      JweWithTestKey.unpack[String](invalid.toString()) mustBe Failure(PayloadError)
    }

    "produce a valid case class with extra JSON from relevant sample JSON" in {
      val wibble = JweWithTestKey.unpack[JweTestModelWithJson](  s"""{"x":"foo", "y":"bar", "json": { "a":"1", "b": 2 } }""" )
      wibble mustBe Success(JweTestModelWithJson("foo", "bar", Json.parse(s"""{ "a":"1", "b": 2 }""")))
    }

    "can unpack to JSON" in new Setup {
      val json = s"""{"x":"foo", "y":"bar", "json": { "a":"1", "b": 2 } }"""
      val wibble = JweWithTestKey.unpack[JsValue]( json )
      wibble mustBe Success(Json.parse(json))
    }
  }

  "JWE Encryptor when encrypting a payload" should {

    val key = "0123456789ABCDEF0123456789ABCDEF"

    "produce a valid JOSE header" in new Setup {
      val jwe = jweOverrideKey(key)

      val token = jwe.encrypt[JweTestModel](JweTestModel("foo", "bar")).get

      token must fullyMatch regex JwePattern
      token must fullyMatch regex Base64chars
      token mustBe a[String]

      val JweGroupsExp(header, _, _, _, _) = token
      header must startWith ("eyJhbG")

      val json = Json.parse(base64decode(header))

      (json \ "alg").asOpt[String].getOrElse("") mustBe "dir"
      (json \ "enc").asOpt[String].getOrElse("") mustBe "A128CBC-HS256"
    }

    "contain expected JSON payload" in {
      val payload = JweTestModel("foo", "bar")
      val token = JweWithTestKey.encrypt[JweTestModel](payload).get

      token must fullyMatch regex JwePattern

      JweWithTestKey.decryptRaw(token) mustBe Success("""{"x":"foo","y":"bar"}""")
    }

    "give back the original payload from a Wibble case class instance" in {
      val payload = JweTestModel("foo", "bar")
      val token = JweWithTestKey.encrypt[JweTestModel](payload).get

      token must fullyMatch regex JwePattern

      JweWithTestKey.decrypt[JweTestModel](token) mustBe Success(payload)
    }

    "return None when an exception is thrown - incorrect key" in new Setup {
      jweWrongKey.encrypt(JweTestModel("foo", "bar")) mustBe None
    }
  }

  "JWE Decryptor when decrypting a payload" should {

    def getTestEncryptor(key: AesKey, headerAlg : String = KMAI.DIRECT): JWE = {
      val encryptor = new JWE()
      encryptor.setKey(key)
      encryptor.setAlgorithmHeaderValue(headerAlg)
      encryptor.setEncryptionMethodHeaderParameter(CEAI.AES_128_CBC_HMAC_SHA_256)
      encryptor
    }

    "pull back a consistent payload after encrypt / decrypt for DIRECT header" in {
      val keytext = JweWithTestKey.key
      val jwe = getTestEncryptor(getKey(keytext), KMAI.DIRECT)
      jwe.setPayload(fooBarJson)
      val jweToken = jwe.getCompactSerialization

      JweWithTestKey.decrypt[JweTestModel](jweToken) match {
        case Success(s) => s mustBe JweTestModel("foo", "bar")
        case unexpected => fail(s"Test failed with unexpected result - ${unexpected}")
      }
    }

    "check an invalid JSON doc" in {
      val jwe = getTestEncryptor(getKey(JweWithTestKey.key), KMAI.DIRECT)
      jwe.setPayload("""{xxx""")
      val jweToken = jwe.getCompactSerialization

      JweWithTestKey.decrypt[JsValue](jweToken) mustBe Failure(PayloadError)

    }

    "check valid but incorrect JSON" in {
      val jwe = getTestEncryptor(getKey(JweWithTestKey.key), KMAI.DIRECT)
      jwe.setPayload("""{"a":"b"}""")
      val jweToken = jwe.getCompactSerialization

      JweWithTestKey.decrypt[JweTestModel](jweToken) mustBe Failure(PayloadError)
    }

    "fail when decrypting a payload with the wrong key" in {

      val testJwe = new JweCommon {
        override val key = "XXScrsCohoHmrcSh4ar3dK3yF0rJw3XX"
      }

      testJwe.encrypt(Json.parse("""{"x":"foo", "y":"bar"}"""))

      JweWithTestKey.decrypt[JweTestModel]("") mustBe Failure(DecryptionError)
    }

    "fail when decrypting a payload with the right key" in {

      JweWithTestKey.decrypt[JweTestModel]("test123") mustBe Failure(DecryptionError)
    }
  }
}