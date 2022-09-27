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

package services

import helpers.UnitSpec
import models.QuestionnaireModel
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.Future

class QuestionnaireServiceSpec extends UnitSpec with MockitoSugar {

  val mockAuditConnector = mock[AuditConnector]

  class Setup {
    val service = new QuestionnaireService {
      override val auditConnector: AuditConnector = mockAuditConnector
    }
  }

  implicit val hc = HeaderCarrier()

  "sendAuditEventOnSuccessfulSubmission" should{

    val expected = AuditResult.Success
    val minimum = QuestionnaireModel("able",None,"trying","satisfaction",1,"recommend",None)
    val maximum = QuestionnaireModel("able",Some("why"),"trying","satisfaction",1,"recommend",Some("imp"))

    "successfully send a audit event of type QuestionnaireAuditEvent" in new Setup {
      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[ExtendedDataEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(expected))

      await(service.sendAuditEventOnSuccessfulSubmission(minimum)(hc, FakeRequest())) mustBe expected
    }

    "successfully send a audit event with min data" in new Setup {
      when(mockAuditConnector.sendExtendedEvent(ArgumentMatchers.any[ExtendedDataEvent]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any()))
        .thenReturn(Future.successful(expected))

      await(service.sendAuditEventOnSuccessfulSubmission(maximum)(hc, FakeRequest())) mustBe expected
    }
  }
}