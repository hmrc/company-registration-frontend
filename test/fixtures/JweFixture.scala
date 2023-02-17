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

package fixtures

import java.nio.charset.StandardCharsets._
import java.util.Base64

import org.jose4j.keys.AesKey
import org.jose4j.lang.ByteUtil
import play.api.libs.json.{JsValue, Json}
import utils.JweCommon

trait JweFixture {

  object JweWithTestKey extends JweCommon{
    override val key = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"
  }

  case class JweTestModel(x: String, y: String)
  case class JweTestModelWithJson(x: String, y: String, json: JsValue)

  implicit val jweTestFormatter = Json.format[JweTestModel]
  implicit val jweTestWithJsonFormatter = Json.format[JweTestModelWithJson]

  lazy val fooBarJson = s"""{"x":"foo", "y":"bar"}"""
  lazy val additionalJson = s"""{ "a":"1", "b": 2 }"""
  lazy val fooBarExtraJson = s"""{"x":"foo", "y":"bar", "json": $additionalJson }"""
  lazy val JwePattern = """^[^.]*.[^.]*.[^.]*.[^.]*.[^.]*$"""
  lazy val JweGroupsExp = """^([^.]*).([^.]*).([^.]*).([^.]*).([^.]*)$""".r
  lazy val Base64chars = """[A-Za-z0-9.=_-]+"""

  def base64decode(bytes: String) : String = new String(Base64.getDecoder.decode(bytes).map(_.toChar))
  def getKey(keytext: String): AesKey = new AesKey(keytext.getBytes(ISO_8859_1))
  def getRandomKey: AesKey = new AesKey(ByteUtil.randomBytes(16))
}
