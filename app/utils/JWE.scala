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

package utils

import config.AppConfig
import org.jose4j.jwe.{ContentEncryptionAlgorithmIdentifiers => CEAI, JsonWebEncryption => JWE, KeyManagementAlgorithmIdentifiers => KMAI}
import org.jose4j.keys.AesKey
import play.api.libs.json
import play.api.libs.json.Json

import java.nio.charset.{StandardCharsets => CS}
import javax.inject.Inject
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

class Jwe @Inject()(appConfig: AppConfig) extends JweCommon {
  // $COVERAGE-OFF$
  lazy val key = appConfig.servicesConfig.getConfString("JWE.key", throw new Exception("could not find JWE.key"))
  // $COVERAGE-ON$
}

trait JweEncryptor extends Logging {
  val key:String
  def aeskey(keytext: String): AesKey
  def encrypt[A](payload: A)(implicit wts: json.Writes[A]) : Option[String] = {
    try {
      val encryptor = new JWE()
      encryptor.setKey(aeskey(key))
      encryptor.setAlgorithmHeaderValue(KMAI.DIRECT) // COHO currently only support DIRECT
      encryptor.setEncryptionMethodHeaderParameter(CEAI.AES_128_CBC_HMAC_SHA_256)
      val message = Json.stringify(wts.writes(payload))
      encryptor.setPayload(message)
      Some(encryptor.getCompactSerialization)
    } catch {
      case ex: Exception =>
        logger.warn(ex.getMessage)
        None
      }
    }
}

case object DecryptionError extends NoStackTrace
case object PayloadError extends NoStackTrace

trait JweDecryptor extends Logging {
  val key:String
  def aeskey(keytext: String): AesKey
  def decrypt[A](jweMessage:String)(implicit rds: json.Reads[A]) : Try[A] = {
    decryptRaw[A](jweMessage) flatMap {
      payload => unpack[A](payload)
    }
  }

  private[utils] def decryptRaw[A](jweMessage:String) : Try[String] = {
    try {
      val decryptor = new JWE()
      decryptor.setKey(aeskey(key))
      decryptor.setCompactSerialization(jweMessage)
      val payload = decryptor.getPayload
      Success(payload)
    } catch {
      case e: Exception =>
        logger.error(s"[decryptRaw] Could not decrypt payload due to ${e.getMessage}")
        Failure(DecryptionError)
    }
  }

  private[utils] def unpack[A](payload:String)(implicit rds: json.Reads[A]) : Try[A] = {

    Try(Json.parse(payload).validate[A]) flatMap(
      _ fold(
        errs => {
          logger.error(errs.toString())
          Failure(PayloadError)
        },
        valid => Success(valid)
        )
      ) recoverWith {
      case e: Exception =>
        logger.error(s"[unpack] Could not unpack payload due to ${e.getMessage}")
        Failure(PayloadError)
    }
  }
}

trait JweCommon extends JweDecryptor with JweEncryptor {
  val key: String
  def aeskey(keytext: String) = new AesKey(keytext.getBytes(CS.ISO_8859_1))
}