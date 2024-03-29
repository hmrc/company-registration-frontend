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

package mocks

import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, Json}
import repositories.NavModelRepoMongo

import scala.concurrent.Future

trait NavModelRepoMock {
  this: MockitoSugar =>

  val mockNavModelRepo = mock[NavModelRepoMongo]

  val handOffNavModelData = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender1",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        ),
        "5-2" -> NavLinks(
          "testForwardLinkFromSender5.2",
          ""
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        ),
        "5-1" -> NavLinks(
          "testForwardLinkFromReceiver5.1",
          ""
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  def mockGetNavModel(handOffNavModel: Option[HandOffNavModel] = Some(handOffNavModelData)) = {
    when(mockNavModelRepo.getNavModel(ArgumentMatchers.any[String])).thenReturn(Future.successful(handOffNavModel))
  }

  def mockInsertNavModel(registrationID: String = "foo", handOffNavModel: Option[HandOffNavModel] = Some(handOffNavModelData)) = {
    when(mockNavModelRepo.insertNavModel(ArgumentMatchers.any(), ArgumentMatchers.any[HandOffNavModel]())).thenReturn(Future.successful(handOffNavModel))
  }
}
