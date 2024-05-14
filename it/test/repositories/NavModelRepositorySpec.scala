/*
 * Copyright 2021 HM Revenue & Customs
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

package test.repositories

import test.fixtures.HandOffFixtures
import test.itutil.{IntegrationSpecBase, MongoHelper}
import org.mongodb.scala.MongoCommandException
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import repositories.{NavModelRepoImpl, NavModelRepoMongo}
import uk.gov.hmrc.mongo.MongoComponent

class NavModelRepositorySpec extends IntegrationSpecBase with ScalaFutures with Eventually with HandOffFixtures with MongoHelper {

  class Setup {
    val repo = app.injector.instanceOf[NavModelRepoImpl].repository

    await(repo.drop)
    await(repo.ensureIndexes)

    def count = await(repo.count)
  }

  val regId = "regID1"
  val userId = "dummyUserId"

  "insertNavModel" should {

    "successfully insert a record in the correct format" in new Setup {
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.count) mustBe 1
    }

    "successfully return the same handOffModelInserted" in new Setup {
      await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      val navModelReturn = await(repo.getNavModel(regId))
      navModelReturn.get mustBe handOffNavModelDataUpTo3

    }
    "successfully update a record when data changes" in new Setup {
      await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
      await(repo.insertNavModel(regId, handOffNavModelUpdatedUpTo3))
      await(repo.count) mustBe 1
      val navModelReturn = await(repo.getNavModel(regId))
      navModelReturn.get mustBe handOffNavModelUpdatedUpTo3

    }
    "return the handOffModel after insertion" in new Setup {
      await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      val res = await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
      res.get mustBe handOffNavModelDataUpTo3
    }
  }

  "successfully return the same handOffModelInserted" in new Setup {
    await(repo.insertNavModel(regId,handOffNavModelDataUpTo3))
    val navModelReturn = await(repo.getNavModel(regId))
    navModelReturn.get mustBe handOffNavModelDataUpTo3
  }

  "successfully update a record when data changes" in new Setup {
    await(repo.insertNavModel(regId, handOffNavModelDataUpTo3))
    await(repo.insertNavModel(regId, handOffNavModelUpdatedUpTo3))
    await(repo.count) mustBe 1
    val navModelReturn = await(repo.getNavModel(regId))
    navModelReturn.get mustBe handOffNavModelUpdatedUpTo3
  }

  "Indexes" must {

    "be applied when the collection is created" in new Setup {

      val indexList: Seq[String] = await(repo.collection.listIndexes().toFuture()).map(_.toString())
      val containsIndexes: Boolean = eventually {
        indexList.exists(_ contains "lastUpdatedIndex") &&
          indexList.exists(_ contains "5400") &&
          indexList.exists(_ contains "_id_")
      }

      containsIndexes mustBe true
    }

    "when allowReplaceIndexes is false" must {

      "NOT allow index to be replaced" in {

        val repo = app.injector.instanceOf[NavModelRepoImpl].repository
        await(repo.ensureIndexes)

        val indexListBefore: Seq[String] = await(repo.collection.listIndexes().toFuture()).map(_.toString())

        indexListBefore.exists(_ contains "lastUpdatedIndex") && indexListBefore.exists(_ contains "5400") mustBe true

        intercept[MongoCommandException](new NavModelRepoMongo(app.injector.instanceOf[MongoComponent], 12345, false))
          .getCode mustBe 85 //Existing Index Exists with Different Options

        await(repo.drop)
      }
    }

    "when allowReplaceIndexes is true" must {

      "allow index to be replaced with a different for the TTL" in {

        val repo = app.injector.instanceOf[NavModelRepoImpl].repository
        await(repo.ensureIndexes)

        val indexListBefore: Seq[String] = await(repo.collection.listIndexes().toFuture()).map(_.toString())

        indexListBefore.exists(_ contains "lastUpdatedIndex") && indexListBefore.exists(_ contains "5400") mustBe true

        val secondRepoInstance = new NavModelRepoMongo(app.injector.instanceOf[MongoComponent], 12345, true)
        await(secondRepoInstance.ensureIndexes)

        val indexListAfter: Seq[String] = await(secondRepoInstance.collection.listIndexes().toFuture()).map(_.toString())

        indexListAfter.exists(_ contains "lastUpdatedIndex") && indexListAfter.exists(_ contains "12345") mustBe true

        await(secondRepoInstance.drop)
      }
    }
  }
}
