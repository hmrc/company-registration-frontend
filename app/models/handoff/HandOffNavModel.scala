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

package models.handoff

import java.time._

import play.api.libs.json._

case class NavLinks(forward: String,
                    reverse: String)

object NavLinks {
  implicit val formats = Json.format[NavLinks]
}

case class JumpLinks(company_name : String,
                     company_address : String,
                     company_jurisdiction : String)

object JumpLinks {
  implicit val format = Json.format[JumpLinks]
}

case class Sender(nav: Map[String, NavLinks])

case class Receiver(nav: Map[String, NavLinks],
                    jump: Map[String, String] = Map.empty,
                    chData: Option[JsObject] = None)

case class HandOffNavModel(sender: Sender,
                           receiver: Receiver)

object HandOffNavModel {
  implicit val formatNavLinks = Json.format[NavLinks]
  implicit val formatReceiver = Json.format[Receiver]
  implicit val formatSender = Json.format[Sender]
  implicit val formats = Json.format[HandOffNavModel]

  def now: Instant = Instant.now()

  val mongoReads = new Reads[HandOffNavModel] {
    def reads(json: JsValue): JsResult[HandOffNavModel] = {
      (json \ "HandOffNavigation").validate[HandOffNavModel](HandOffNavModel.formats)
    }
  }



  def mongoWrites(registrationID: String, dateTime: Instant = now) = new OWrites[HandOffNavModel] {

    override def writes(hm: HandOffNavModel):JsObject = {
      Json.obj(
        "_id"               -> Json.toJsFieldJsValueWrapper(registrationID),
        "lastUpdated"       -> Json.toJson(dateTime),
        "HandOffNavigation" -> Json.toJsFieldJsValueWrapper(hm)(formats)
      )
    }
  }
}
