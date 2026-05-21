/*
 * Copyright 2026 HM Revenue & Customs
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

package connectors

import play.api.libs.json.Format
import repositories.DataCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataCacheConnector @Inject() (dataCacheRepository: DataCacheRepository)(implicit ec: ExecutionContext) {

  def saveForm[T](userId: String, formId: String, data: T)(implicit hc: HeaderCarrier, ec: ExecutionContext, format: Format[T]): Future[CacheItem] =
    dataCacheRepository.put[T](userId)(DataKey(formId), data)

  def fetchAndGet[T](userId: String, formId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, format: Format[T]): Future[Option[T]] =
    dataCacheRepository.get[T](userId)(DataKey(formId))

  def clear(userId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    dataCacheRepository.deleteEntity(userId)
}
