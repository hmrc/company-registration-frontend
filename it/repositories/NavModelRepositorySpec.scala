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

import fixtures.HandOffFixtures
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global

class NavModelRepositorySpec extends UnitSpec with WithFakeApplication with ScalaFutures with Eventually with HandOffFixtures {

  class Setup {
    val rc = fakeApplication.injector.instanceOf[ReactiveMongoComponent]
    val repo = new NavModelRepoMongo(rc.mongoConnector.db,100)

    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)
  }

  class SetupWithIndexes(indexList: List[Index]) {
    val rc = fakeApplication.injector.instanceOf[ReactiveMongoComponent]
    val repo = new NavModelRepoMongo(rc.mongoConnector.db,100){
      override def additionalIndexes: List[Index] = indexList
    }

    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)
  }

  val regId = "regID1"
  val userId = "dummyUserId"

  "insertNavModel" should {

    "successfully insert a record in the correct format" in new Setup {
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.count) shouldBe 1
    }

    "successfully return the same handOffModelInserted" in new Setup {
      await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      val navModelReturn = await(repo.getNavModel(regId))
      navModelReturn.get shouldBe handOffNavModelDataUpTo3

    }
    "successfully update a record when data changes" in new Setup {
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.insertNavModel(regId, handOffNavModelUpdatedUpTo3))
      await(repo.count) shouldBe 1
      val navModelReturn = await(repo.getNavModel(regId))
      navModelReturn.get shouldBe handOffNavModelUpdatedUpTo3

    }
    "return the handOffModel after insertion" in new Setup {
      await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      val res = await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      res.get shouldBe handOffNavModelDataUpTo3
    }
  }

  "successfully return the same handOffModelInserted" in new Setup {
    await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
    val navModelReturn = await(repo.getNavModel(regId))
    navModelReturn.get shouldBe handOffNavModelDataUpTo3
  }

  "successfully update a record when data changes" in new Setup {
    await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
    await(repo.insertNavModel(regId, handOffNavModelUpdatedUpTo3))
    await(repo.count) shouldBe 1
    val navModelReturn = await(repo.getNavModel(regId))
    navModelReturn.get shouldBe handOffNavModelUpdatedUpTo3
  }

  "Indexes" ignore {

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
