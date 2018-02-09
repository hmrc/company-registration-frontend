/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}

import models.handoff.HandOffNavModel
import play.api.libs.json.JsObject
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.DB
import reactivemongo.bson.{BSONArray, BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NavModelRepo @Inject()(mongo: ReactiveMongoComponent) {
  lazy val repository: NavModelRepoMongo = new NavModelRepoMongo(mongo.mongoConnector.db)

}

object NavModelRepo extends MongoDbConnection with ServicesConfig {
  lazy val repository: NavModelRepoMongo = new NavModelRepoMongo(db)
  lazy val expireAfterSeconds = getConfInt("navModel-time-to-live.ttl", throw new Exception("could not find config key navModel-time-to-live.ttl"))
}

trait NavModelRepository {
  def getNavModel(registrationID:String):Future[Option[HandOffNavModel]]
  def insertNavModel(registrationID: String,hm:HandOffNavModel): Future[Option[HandOffNavModel]]
}

class NavModelRepoMongo(mongo: () => DB) extends ReactiveRepository[HandOffNavModel, BSONObjectID]("NavModel", mongo,HandOffNavModel.formats)
  with NavModelRepository
  with TTLIndexing[HandOffNavModel, BSONObjectID] {

  override val expireAfterSeconds: Long = NavModelRepo.expireAfterSeconds

  //TODO: Remove $or statements after 25th August to use selecting on ONLY '_id'
  override def getNavModel(registrationID: String): Future[Option[HandOffNavModel]] = {
    //val selector = BSONDocument("_id" -> registrationID)
    val selector = BSONDocument(
      "$or" -> BSONArray(
        BSONDocument("registrationID" -> registrationID),
        BSONDocument("_id"            -> registrationID)
      )
    )
    collection.find(selector).one[HandOffNavModel](HandOffNavModel.mongoReads, implicitly[ExecutionContext])
  }

  override def insertNavModel(registrationID: String, hm : HandOffNavModel): Future[Option[HandOffNavModel]] = {
    //val selector = BSONDocument("_id" -> registrationID)
    val selector = BSONDocument(
      "$or" -> BSONArray(
        BSONDocument("registrationID" -> registrationID),
        BSONDocument("_id"            -> registrationID)
      )
    )
    val js: JsObject = HandOffNavModel.mongoWrites(registrationID).writes(hm)

    collection.findAndUpdate(selector, js, upsert = true, fetchNewObject = true).map{
      s => s.result[HandOffNavModel](HandOffNavModel.mongoReads)
    }
  }
}
