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

package repositories

import models.handoff.{HandOffNavModel, NavLinks, Receiver, Sender}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.api.indexes.{Index, IndexType}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class NavModelRepositorySpec extends UnitSpec with MongoSpecSupport with WithFakeApplication with ScalaFutures with Eventually {

  class Setup {

    val repo = new NavModelRepoMongo(mongo)

    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)
  }

  class SetupWithIndexes(indexList: List[Index]) {

    val repo = new NavModelRepoMongo(mongo){
      override def additionalIndexes: List[Index] = indexList
    }

    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)
  }

  val registrationID = "regID1"
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
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  val handOffNavModelUpdated = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender17373737373",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
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
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  "insertNavModel" should {

    "successfully insert a record in the correct format" in new Setup {
      await(repo.insertNavModel(registrationID, handOffNavModelData))
      await(repo.count) shouldBe 1
    }

    "successfully return the same handOffModelInserted" in new Setup {
      await(repo.insertNavModel(registrationID,handOffNavModelData))
      val navModelReturn = await(repo.getNavModel(registrationID))
      navModelReturn.get shouldBe handOffNavModelData

    }
    "successfully update a record when data changes" in new Setup {
      await(repo.insertNavModel(registrationID, handOffNavModelData))
      await(repo.insertNavModel(registrationID, handOffNavModelUpdated))
      await(repo.count) shouldBe 1
      val navModelReturn = await(repo.getNavModel(registrationID))
      navModelReturn.get shouldBe handOffNavModelUpdated

    }
    "return the handOffModel after insertion" in new Setup {
      await(repo.insertNavModel(registrationID,handOffNavModelData))
      val res = await(repo.insertNavModel(registrationID,handOffNavModelData))
      res.get shouldBe handOffNavModelData
    }
  }

  "successfully return the same handOffModelInserted" in new Setup {
    await(repo.insertNavModel(registrationID,handOffNavModelData))
    val navModelReturn = await(repo.getNavModel(registrationID))
    navModelReturn.get shouldBe handOffNavModelData
  }

  "successfully update a record when data changes" in new Setup {
    await(repo.insertNavModel(registrationID, handOffNavModelData))
    await(repo.insertNavModel(registrationID, handOffNavModelUpdated))
    await(repo.count) shouldBe 1
    val navModelReturn = await(repo.getNavModel(registrationID))
    navModelReturn.get shouldBe handOffNavModelUpdated
  }

  "Indexes" should {

    val additionalIndex = List(Index(
      key = Seq("test" -> IndexType.Ascending),
      name = Some("testIndex")
    ))

    "be applied when the collection is created" in new Setup {

      val indexList: List[Index] = await(repo.collection.indexesManager.list())

      val containsIndexes: Boolean = eventually {
        indexList.map(_.name).filter(_.isDefined).contains(Some("lastUpdatedIndex")) &&
          indexList.map(_.name).filter(_.isDefined).contains(Some("_id_"))
      }

      containsIndexes shouldBe true
    }

    "be applied when the collection is created along with any additional indexes" in new SetupWithIndexes(additionalIndex) {

      val indexList: List[Index] = await(repo.collection.indexesManager.list())

      val containsIndexes: Boolean = eventually {
        indexList.map(_.name).filter(_.isDefined).contains(Some("lastUpdatedIndex")) &&
          indexList.map(_.name).filter(_.isDefined).contains(Some("testIndex")) &&
            indexList.map(_.name).filter(_.isDefined).contains(Some("_id_"))
      }

      containsIndexes shouldBe true
    }
  }
}
