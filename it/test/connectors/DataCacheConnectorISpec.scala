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

import models.AccountingDatesModel
import play.api.libs.json.{JsObject, Json, Reads}
import test.itutil.IntegrationSpecBase
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

class DataCacheConnectorISpec extends IntegrationSpecBase {

  lazy val connector: DataCacheConnector =
    app.injector.instanceOf[DataCacheConnector]

  implicit val reads: Reads[AccountingDatesModel] = Json.reads[AccountingDatesModel]

  val userId = "user-1"
  val formId = "test-form"

  val model: AccountingDatesModel =
    AccountingDatesModel("", Some("1"), Some("1"), Some("2019"))

  val expectedJson: JsObject = Json.obj(
    formId -> Json.obj(
      "crnDate" -> "",
      "year"    -> "1",
      "month"   -> "1",
      "day"     -> "2019"
    )
  )

  "DataCacheConnector.saveForm" should {
    "store data in cache" in {

      val result = await(connector.saveForm(userId, formId, model))

      result.data mustBe expectedJson
    }
  }

  "DataCacheConnector.fetchAndGet" should {
    "return previously stored model" in {

      await(connector.saveForm(userId, formId, model))

      val fetched =
        await(connector.fetchAndGet[AccountingDatesModel](userId, formId))

      fetched mustBe Some(model)
    }
  }

  "DataCacheConnector.clear" should {
    "remove all cached data for user" in {

      await(connector.saveForm(userId, formId, model))

      await(connector.clear(userId))

      val fetched =
        await(connector.fetchAndGet[AccountingDatesModel](userId, formId))

      fetched mustBe None
    }
  }
}
