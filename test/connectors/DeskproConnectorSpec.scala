/*
 * Copyright 2023 HM Revenue & Customs
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

import helpers.SCRSSpec
import mocks.{MetricServiceMock, SCRSMocks}
import models.external.Ticket
import play.api.libs.json.{JsObject, Json}
import services.MetricsService
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}

import scala.concurrent.Future

class DeskproConnectorSpec extends SCRSSpec with SCRSMocks {

  class Setup {
    val connector = new DeskproConnector {
      override val deskProUrl: String = "http://testUrl"
      override val httpClientV2 = mockHttpClientV2
      override val metricsService: MetricsService = MetricServiceMock
    }

    implicit val hc = HeaderCarrier()
  }

  val ticket: Ticket = Ticket(
    name = "Mr Bobby B. Bobblington III MBE BSc",
    email = "thebigb@testmail.test",
    subject = "companyReg DeskPro ticket",
    message = "testMessage",
    referrer = "testUrl",
    javascriptEnabled = "Y",
    userAgent = "company-registration-frontend",
    authId = "test/test/test",
    areaOfTax = "unknown",
    sessionId = "testsession-123456",
    service = "scrs"
  )

  val ticketNum = 123456789
  val response : JsObject = Json.obj("ticket_id" -> ticketNum)

  "submitTicket" should {
    "return a ticket number" in new Setup {
      mockHttpPOST[Ticket, JsObject](Future.successful(response))

      await(connector.submitTicket(ticket)) mustBe ticketNum
    }

    "throw a bad request exception" in new Setup {
      mockHttpFailedPOST[Ticket, JsObject](new BadRequestException("404"))

      intercept[BadRequestException](await(connector.submitTicket(ticket)))
    }

    "throw any other exception" in new Setup {
      mockHttpFailedPOST[Ticket, JsObject](new RuntimeException)

      intercept[RuntimeException](await(connector.submitTicket(ticket)))
    }
  }
}
