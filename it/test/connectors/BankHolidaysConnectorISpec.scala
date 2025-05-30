/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import services.BankHolidaysService.GDSBankHolidays
import test.itutil.{IntegrationSpecBase, WiremockHelper}
import uk.gov.hmrc.http.HeaderCarrier

class BankHolidaysConnectorISpec  extends IntegrationSpecBase {

  implicit val hc = HeaderCarrier()

  override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(Map("microservice.services.address-lookup-frontend.port" -> s"${WiremockHelper.wiremockPort}"))
    .build

  lazy val connector = app.injector.instanceOf[BankHolidaysConnector]

  "BankHolidaysConnector" should {

    "calling getBankHolidays" in {

      val result = await(connector.getBankHolidays())
      result.status mustBe 200
      (result.json.validate[GDSBankHolidays].get.`england-and-wales`.events.size > 1) mustBe true
    }
  }
}
