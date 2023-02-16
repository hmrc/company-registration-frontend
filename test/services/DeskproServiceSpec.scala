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

package services

import connectors._
import helpers.SCRSSpec
import mocks.SCRSMocks
import models.Ticket
import models.external.{Ticket => ApiTTicket}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import uk.gov.hmrc.http.{HeaderCarrier,SessionId}

import scala.concurrent.Future

class DeskproServiceSpec extends SCRSSpec {

  val mockdeskproConnector = mock[DeskproConnector]

  trait Setup {
    val service = new DeskproService {
      override val deskproConnector: DeskproConnector = mockdeskproConnector
    }
  }

  val regId = "12345"
  val mockAuthId = "auth/oid/1234567890"

  val sessionId = "session-123456-654321"
  val mockSession = SessionId(sessionId)
  override implicit val hc = HeaderCarrier(sessionId = Some(mockSession))

  "getSessionId" should {
    "return a session Id" in new Setup {
      service.getSessionId mustBe sessionId
    }
  }

  val name = "Mr Bobby B. Bobblington III MBE BSc"
  val email = "thebigbn@testmail.test"
  val message = "testMessage"

  val ticket: ApiTTicket = ApiTTicket(
    name = name,
    email = email,
    subject = s"Company Registration submission failed for Registration ID: $regId",
    message = message,
    referrer = "https://www.tax.service.gov.uk/register-your-company",
    javascriptEnabled = "Y",
    userAgent = "company-registration-frontend",
    authId = mockAuthId,
    areaOfTax = "unknown",
    sessionId = sessionId,
    service = "SCRS"
  )

  val providedInfo = Ticket(name, email, message)

  "buildTicket" should {
    "return a new ticket" in new Setup {
      await(service.buildTicket(regId, providedInfo, mockAuthId)) mustBe ticket
    }
  }

  "submitTicket" should {

    val ticketResponse : Long = 123456789

    "return a ticket id fromt DeskPro" in new Setup {
      when(mockdeskproConnector.submitTicket(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(ticketResponse))

      await(service.submitTicket(regId, providedInfo, mockAuthId)) mustBe ticketResponse
    }
  }

}
