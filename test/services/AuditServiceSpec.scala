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

import java.time.Instant
import java.util.UUID

import audit.events.ContactDetailsAuditEventDetail
import helpers.UnitSpec
import models.{CompanyContactDetails, Links}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.play.audit.AuditExtensions.auditHeaderCarrier
import uk.gov.hmrc.play.audit.http.config.AuditingConfig
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.{ExecutionContext, Future}


class AuditServiceSpec extends UnitSpec with MockitoSugar {

  class Setup(otherHcHeaders: Seq[(String, String)] = Seq()) {

    implicit val hc = HeaderCarrier(sessionId = Some(SessionId("session-123")), otherHeaders = otherHcHeaders)
    implicit val ec = ExecutionContext.global
    implicit val request = FakeRequest()

    val mockAuditConnector = mock[AuditConnector]
    val mockAuditingConfig = mock[AuditingConfig]

    val regId = "12345"
    val instantNow = Instant.now()
    val appName = "business-registration-notification"
    val auditType = "testAudit"
    val testEventId = UUID.randomUUID().toString
    val txnName = "transactionName"

    when(mockAuditConnector.auditingConfig) thenReturn mockAuditingConfig
    when(mockAuditingConfig.auditSource) thenReturn appName

    val amendedContactDetails = CompanyContactDetails(
      Some("afoo@bar.wibble"),
      Some("012345"),
      Some("012345"),
      Links(Some("link"))
    )

    val event = ContactDetailsAuditEventDetail(
      "extID", "regID", "credID", "foo@bar.wibble", amendedContactDetails
    )

    val service = new AuditService {

      override val auditConnector = mockAuditConnector

      override private[services] def now() = instantNow
      override private[services] def eventId() = testEventId
    }
  }

  "auditPAYEContactDetails" should {
    val tstContactDetails = CompanyContactDetails(
      Some("afoo@bar.wibble"),
      Some("012345"),
      Some("012345"),
      Links(Some("link"))
    )

    ".sendEvent" when {

      "call to AuditConnector is successful" when {

        "transactionName is provided and path does NOT exist" must {

          "create and send an Explicit ExtendedAuditEvent including the transactionName with pathTag set to '-'" in new Setup {

            when(
              mockAuditConnector.sendExtendedEvent(
                ArgumentMatchers.eq(ExtendedDataEvent(
                  auditSource = appName,
                  auditType = auditType,
                  eventId = testEventId,
                  tags = hc.toAuditTags(txnName, "-"),
                  detail = Json.toJson(event),
                  generatedAt = instantNow
                ))
              )(
                ArgumentMatchers.eq(hc),
                ArgumentMatchers.eq(ec)
              )
            ) thenReturn Future.successful(AuditResult.Success)

            val actual = await(service.sendEvent(auditType, event, Some(txnName)))

            actual mustBe AuditResult.Success
          }
        }

        "transactionName is NOT provided and path exists" must {

          "create and send an Explicit ExtendedAuditEvent with transactionName as auditType & pathTag extracted from the HC" in new Setup(
            otherHcHeaders = Seq("path" -> "/wizz/foo/bar")
          ) {

            when(
              mockAuditConnector.sendExtendedEvent(
                ArgumentMatchers.eq(ExtendedDataEvent(
                  auditSource = appName,
                  auditType = auditType,
                  eventId = testEventId,
                  tags = hc.toAuditTags(auditType, "/wizz/foo/bar"),
                  detail = Json.toJson(event),
                  generatedAt = instantNow
                ))
              )
              (
                ArgumentMatchers.eq(hc),
                ArgumentMatchers.eq(ec)
              )
            ) thenReturn Future.successful(AuditResult.Success)

            val actual = await(service.sendEvent(auditType, event, None))

            actual mustBe AuditResult.Success
          }
        }
      }

      "call to AuditConnector fails" must {

        "throw the exception" in new Setup {

          val exception = new Exception("Oh No")

          when(
            mockAuditConnector.sendExtendedEvent(
              ArgumentMatchers.eq(ExtendedDataEvent(
                auditSource = appName,
                auditType = auditType,
                eventId = testEventId,
                tags = hc.toAuditTags(txnName, "-"),
                detail = Json.toJson(event),
                generatedAt = instantNow
              ))
            )
            (
              ArgumentMatchers.eq(hc),
              ArgumentMatchers.eq(ec)
            )
          ) thenReturn Future.failed(exception)

          val actual = intercept[Exception](await(service.sendEvent(auditType, event, Some(txnName))))

          actual.getMessage mustBe exception.getMessage
        }
      }
    }
  }
}
