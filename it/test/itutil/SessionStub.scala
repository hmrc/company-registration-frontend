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

package itutil

import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{Reads, Writes}
import repositories.SessionCacheRepository
import test.itutil.{IntegrationSpecBase, LoginStub}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId => HttpSessionId}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongo.test.MongoSupport

import scala.concurrent.ExecutionContext.Implicits.global

trait SessionStub extends LoginStub with MongoSupport with BeforeAndAfterEach {
  self: IntegrationSpecBase =>

  lazy val repo: SessionCacheRepository =
    app.injector.instanceOf[SessionCacheRepository]

  implicit lazy val hc: HeaderCarrier =
    HeaderCarrier(sessionId = Some(HttpSessionId(SessionId)))

  override def beforeEach(): Unit = {
    super.beforeEach()

    await(repo.cacheRepo.collection.drop().toFuture())

    await(
      repo.cacheRepo.collection.countDocuments().toFuture()
    ) mustBe 0

    resetWiremock()
  }

  def verifySessionCacheData[T](
      key: DataKey[T],
      expectedData: Option[T]
  )(implicit reads: Reads[T]): Unit = {

    val actualData =
      await(repo.getFromSession(key))

    if (actualData != expectedData) {
      throw new Exception(
        s"""Data in cache doesn't match expected data:
           |expected = $expectedData
           |actual   = $actualData
           |""".stripMargin
      )
    }
  }

  def cacheSessionData[T](
      key: DataKey[T],
      data: T
  )(implicit writes: Writes[T]): Unit =
    await(repo.putSession(key, data))

  def clearSessionCache(): Unit =
    await(repo.deleteFromSession)
}
