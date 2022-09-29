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

package repositories

import akka.actor.ActorSystem
import config.AppConfig
import models.handoff.{HandOffNavModel, MongoHandOffNavModel}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, FindOneAndReplaceOptions, IndexModel, IndexOptions, ReturnDocument}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt


class NavModelRepoImpl @Inject()(val mongo: MongoComponent,
                                 val appConfig: AppConfig) extends NavModelRepo

trait NavModelRepo {
  val mongo: MongoComponent
  val appConfig: AppConfig

  lazy val allowReplaceIndexes: Boolean = appConfig.servicesConfig.getString("mongodb.allowReplaceIndexes").toBoolean
  lazy val teardownBrokenRecords: Boolean = appConfig.servicesConfig.getString("mongodb.teardownBrokenRecords").toBoolean
  lazy val expireAfterSeconds: Long = appConfig.servicesConfig.getConfInt("navModel-time-to-live.ttl", throw new Exception("could not find config key navModel-time-to-live.ttl"))
  lazy val repository: NavModelRepoMongo = new NavModelRepoMongo(mongo, expireAfterSeconds, allowReplaceIndexes, teardownBrokenRecords)
}
trait NavModelRepository {
  def getNavModel(registrationID:String):Future[Option[HandOffNavModel]]
  def insertNavModel(registrationID: String,hm:HandOffNavModel): Future[Option[HandOffNavModel]]
}

class NavModelRepoMongo(mongo: MongoComponent, expireSeconds: Long, allowReplaceIndexes: Boolean, teardownBrokenRecords: Boolean) extends PlayMongoRepository[MongoHandOffNavModel](
  mongoComponent = mongo,
  collectionName = "NavModel",
  domainFormat = MongoHandOffNavModel.format,
  indexes = Seq(
    IndexModel(
      ascending("lastUpdated"),
      IndexOptions()
        .name("lastUpdatedIndex")
        .expireAfter(expireSeconds, TimeUnit.SECONDS)
    )
  ),
  replaceIndexes = allowReplaceIndexes
) with NavModelRepository with Logging {

  override def getNavModel(registrationID: String): Future[Option[HandOffNavModel]] =
    collection.find(equal("_id", registrationID)).map(_.handOffNavigation).headOption()

  override def insertNavModel(registrationID: String, hm : HandOffNavModel): Future[Option[HandOffNavModel]] =
    collection.findOneAndReplace(
      filter = equal("_id", registrationID),
      replacement = MongoHandOffNavModel(registrationID, hm),
      options = FindOneAndReplaceOptions()
        .upsert(true)
        .returnDocument(ReturnDocument.AFTER)
    ).map(_.handOffNavigation).headOption()

  if(teardownBrokenRecords) {
    ActorSystem("mongoStartupTeardownJob").scheduler.scheduleOnce(5.seconds) {
      collection.deleteMany(
        Filters.not(Filters.exists("lastUpdated"))
      ).toFuture().map { result =>
        logger.warn(s"[NavModelRepoMongo][mongoStartupTeardownJob] Ran and deleted: '${result.getDeletedCount}' documents")
      }
    }
  }
}