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

package services

import connectors.DataCacheConnector
import play.api.libs.json.Format
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DataCacheService @Inject() (
    dataCacheConnector: DataCacheConnector
)(implicit ec: ExecutionContext) {

  def saveForm[T](
      userId: String,
      formId: String,
      data: T
  )(implicit
      hc: HeaderCarrier,
      format: Format[T]
  ): Future[CacheItem] =
    dataCacheConnector.saveForm(userId, formId, data)

  def fetchForm[T](
      userId: String,
      formId: String
  )(implicit
      hc: HeaderCarrier,
      format: Format[T]
  ): Future[Option[T]] =
    dataCacheConnector.fetchAndGet[T](userId, formId)

  def clearCache(
      userId: String
  )(implicit
      hc: HeaderCarrier
  ): Future[Unit] =
    dataCacheConnector.clear(userId)
}
