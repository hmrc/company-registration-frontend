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

package utils

import ch.qos.logback.classic.Level
import connectors.CompanyRegistrationConnector
import helpers.{LogCapturing, UnitSpec}
import mocks.{CompanyRegistrationConnectorMock, SessionCacheServiceMock}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfter, LoneElement}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results
import play.api.test.Helpers._
import services.SessionCacheService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class SessionRegistrationSpec
    extends UnitSpec
    with CompanyRegistrationConnectorMock
    with SessionCacheServiceMock
    with MockitoSugar
    with BeforeAndAfter
    with LogCapturing
    with LoneElement
    with Eventually {

  implicit val hc: HeaderCarrier                          = HeaderCarrier()
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val mockSessionCache: SessionCacheService = mockSessionCacheService

  object SessionRegistration extends SessionRegistration {
    val sessionCacheService: SessionCacheService       = mockSessionCache
    val compRegConnector: CompanyRegistrationConnector = mockCompanyRegistrationConnector
    override implicit val ec: ExecutionContext         = executionContext
  }

  before {
    reset(mockSessionCache)
  }

  "SessionRegistration" should {

    "redirect to post-sign-in if there's no reg id in the session" in {

      mockSessionCacheGet("registrationID", None)

      val result = SessionRegistration.registered { redirect =>
        Future.successful(Results.Ok)
      }
      val response = await(result)
      response.header.status mustBe SEE_OTHER
    }

    "return an Ok status when there is a reg id in the session" in {

      mockSessionCacheGet("registrationID", Some("1"))

      val result = SessionRegistration.registered { redirect =>
        Future.successful(Results.Ok)
      }
      val response = await(result)
      response.header.status mustBe OK
    }
    "log an error if no reg id exists" in {

      mockSessionCacheGet("registrationID", None)

      withCaptureOfLoggingFrom(SessionRegistration.logger) { logEvents =>
        val result = SessionRegistration.registered { redirect =>
          Future.successful(Results.Ok)
        }
        val response = await(result)
        response.header.status mustBe SEE_OTHER

        await(logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage) must include(
          s"[SessionRegistration][registered] returned None from session cache mongo store when fetching a registrationID")
      }
    }
  }

  "checkStatuses should return Ok (reg id) if regid status does not match any in seq passed in" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo", rid = "1"))

    val result = SessionRegistration.checkStatuses { redirect =>
      Future.successful(Results.Ok)
    }

    val response = await(result)
    response.header.status mustBe OK

  }
  "checkStatuses should return a 303 redirect to post sign in if status from ct matches seq of statuses" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "draft"))

    val result = SessionRegistration.checkStatuses { redirect =>
      Future.successful(Results.Ok)
    }

    val response = await(result)
    response.header.status mustBe SEE_OTHER

  }
  "checkStatuses should return a 303 redirect to post sign in if status from ct matches seq of statuses passed in" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "bar"))

    val result = SessionRegistration.checkStatuses(f = redirect => Future.successful(Results.Ok), statuses = Seq("foo", "bar"))

    val response = await(result)
    response.header.status mustBe SEE_OTHER

  }
  "checkStatuses should return a 303  redirect to post sign in, if user is not registered" in {
    mockSessionCacheGet("registrationID", None)

    val result = SessionRegistration.checkStatuses(f = redirect => Future.successful(Results.Ok), statuses = Seq("foo", "bar"))

    val response = await(result)
    response.header.status mustBe SEE_OTHER

  }

  "checkStatus should return OK if status is draft" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "draft"))
    val result = SessionRegistration.checkStatus(f = redirect => Future.successful(Results.Ok))

    val response = await(result)

    response.header.status mustBe OK

  }

  "checkStatus should redirect to dashboard if status is not draft or locked" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
    val result = SessionRegistration.checkStatus(f = redirect => Future.successful(Results.Ok))
    status(result) mustBe SEE_OTHER
    redirectLocation(result) mustBe Some(controllers.dashboard.routes.DashboardController.show.toString)
  }

  "checkStatus should redirect to post-sign-in if status is locked" in {
    mockSessionCacheGet("registrationID", Some("1"))
    CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "locked"))
    val result = SessionRegistration.checkStatus(f = redirect => Future.successful(Results.Ok))

    status(result) mustBe SEE_OTHER
    redirectLocation(result) mustBe Some(controllers.reg.routes.SignInOutController.postSignIn(None).toString)
  }
}
