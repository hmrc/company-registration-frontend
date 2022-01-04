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

import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong}
import uk.gov.hmrc.mongo.ReactiveRepository
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait TTLIndexing[A, ID] {
  self: ReactiveRepository[A, ID] =>

  val expireAfterSeconds: Long

  private lazy val LastUpdatedIndex = "lastUpdatedIndex"
  private lazy val EXPIRE_AFTER_SECONDS = "expireAfterSeconds"

  def additionalIndexes: List[Index] = List.empty[Index]

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    import reactivemongo.bson.DefaultBSONHandlers._

    collection.indexesManager.list().flatMap {
      indexes => {
        val indexToUpdate = indexes.find(index =>
          index.eventualName == LastUpdatedIndex
            && index.options.getAs[BSONLong](EXPIRE_AFTER_SECONDS).getOrElse(BSONLong(expireAfterSeconds)).as[Long] != expireAfterSeconds
        )

        indexToUpdate match {
          case Some(index) =>
            for {
              deleted <- collection.indexesManager.drop(index.eventualName)
              updated <- ensureCustomIndexes(additionalIndexes)
            } yield updated
          case None =>
            ensureCustomIndexes(additionalIndexes)
        }
      }
    }
    ensureCustomIndexes(additionalIndexes)
  }

  private def ensureCustomIndexes(otherIndexes: Seq[Index])(implicit ec: ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(Seq(collection.indexesManager.ensure(
      Index(
        key = Seq("lastUpdated" -> IndexType.Ascending),
        name = Some(LastUpdatedIndex),
        options = BSONDocument(EXPIRE_AFTER_SECONDS -> BSONLong(5400))
      )
    )) ++ otherIndexes.map(collection.indexesManager.ensure))
  }
}
