/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject

import config.FrontendAppConfig
import models.handoff.HandOffNavModel
import play.api.Logger
import play.api.libs.json.JsObject
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import reactivemongo.bson.{BSONArray, BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class NavModelRepoImpl @Inject()(val mongo: ReactiveMongoComponent,
                                 val appConfig: FrontendAppConfig) extends NavModelRepo

trait NavModelRepo {
  val mongo: ReactiveMongoComponent
  val appConfig: FrontendAppConfig

  lazy val expireAfterSeconds: Long = appConfig.servicesConfig.getConfInt("navModel-time-to-live.ttl", throw new Exception("could not find config key navModel-time-to-live.ttl"))
  lazy val repository: NavModelRepoMongo = new NavModelRepoMongo(mongo.mongoConnector.db, expireAfterSeconds)
}
trait NavModelRepository {
  def getNavModel(registrationID:String):Future[Option[HandOffNavModel]]
  def insertNavModel(registrationID: String,hm:HandOffNavModel): Future[Option[HandOffNavModel]]
}

class NavModelRepoMongo(mongo: () => DB, expireSeconds: Long) extends ReactiveRepository[HandOffNavModel, BSONObjectID]("NavModel", mongo,HandOffNavModel.formats)
  with NavModelRepository
  with TTLIndexing[HandOffNavModel, BSONObjectID] {

  override lazy val expireAfterSeconds: Long = expireSeconds

  indexEnsurer("nav-model-repo")

  def indexEnsurer(name: String): Unit = {
    ensureIndexes map {
      r => {
        Logger.info( s"Ensure Indexes for ${name} returned ${r}" )
        Logger.info( s"Repo ${name} has ${indexes.size} indexes" )
        indexes map { index =>
          val name = index.name.getOrElse("<no-name>")
          Logger.info(s"Repo:${name} Index:${name} Details:${index}")
        }
      }
    }
  }

  //TODO: Remove $or statements after 25th August to use selecting on ONLY '_id'
  override def getNavModel(registrationID: String): Future[Option[HandOffNavModel]] = {
    val selector = BSONDocument(
      "$or" -> BSONArray(
        BSONDocument("registrationID" -> registrationID),
        BSONDocument("_id"            -> registrationID)
      )
    )
    collection.find(selector).one[HandOffNavModel](HandOffNavModel.mongoReads, implicitly[ExecutionContext])
  }

  override def insertNavModel(registrationID: String, hm : HandOffNavModel): Future[Option[HandOffNavModel]] = {
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