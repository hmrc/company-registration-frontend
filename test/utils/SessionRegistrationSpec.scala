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

package utils

import ch.qos.logback.classic.Level
import helpers.{LogCapturing, UnitSpec}
import mocks.{CompanyRegistrationConnectorMock, KeystoreMock}
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, LoneElement}
import play.api.Logger
import play.api.mvc.Results
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class SessionRegistrationSpec extends UnitSpec with CompanyRegistrationConnectorMock with KeystoreMock with MockitoSugar with BeforeAndAfter with LogCapturing with LoneElement with Eventually {

    implicit val hc = HeaderCarrier()

    val mockKeyStore = mockKeystoreConnector

    object SessionRegistration extends SessionRegistration {
        val keystoreConnector = mockKeyStore
        val compRegConnector = mockCompanyRegistrationConnector
    }

    before {
        reset(mockKeyStore)
    }

    "SessionRegistration" should {

        "redirect to post-sign-in if there's no reg id in the session" in {

            mockKeystoreFetchAndGet("registrationID", None)

            val result = SessionRegistration.registered { redirect =>
                Future.successful(Results.Ok)
            }
            val response = await(result)
            response.header.status mustBe SEE_OTHER
        }

        "return an Ok status when there is a reg id in the session" in {

            mockKeystoreFetchAndGet("registrationID", Some("1"))

            val result = SessionRegistration.registered { redirect =>
                Future.successful(Results.Ok)
            }
            val response = await(result)
            response.header.status mustBe OK
        }
      "log an error if no reg id exists" in {

        mockKeystoreFetchAndGet("registrationID", None)

        withCaptureOfLoggingFrom(SessionRegistration.logger) { logEvents =>

          val result = SessionRegistration.registered { redirect =>
            Future.successful(Results.Ok)
          }
          val response = await(result)
          response.header.status mustBe SEE_OTHER

           await(logEvents.filter(_.getLevel == Level.ERROR).loneElement.getMessage) must include(s"[SessionRegistration][registered] returned None from keystore when fetching a registrationID")
        }
      }
    }

    "checkStatuses should return Ok (reg id) if regid status does not match any in seq passed in" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo",rid = "1"))

        val result = SessionRegistration.checkStatuses{redirect =>
            Future.successful(Results.Ok)}

        val response = await(result)
        response.header.status mustBe OK

    }
    "checkStatuses should return a 303 redirect to post sign in if status from ct matches seq of statuses" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "draft"))

        val result = SessionRegistration.checkStatuses{redirect =>
            Future.successful(Results.Ok)}

        val response = await(result)
        response.header.status mustBe SEE_OTHER

    }
    "checkStatuses should return a 303 redirect to post sign in if status from ct matches seq of statuses passed in" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "bar"))

        val result = SessionRegistration.checkStatuses(f = redirect =>
            Future.successful(Results.Ok), statuses = Seq("foo","bar"))

        val response = await(result)
        response.header.status mustBe SEE_OTHER


    }
    "checkStatuses should return a 303  redirect to post sign in, if user is not registered" in {
        mockKeystoreFetchAndGet("registrationID", None)


        val result = SessionRegistration.checkStatuses(f = redirect =>
            Future.successful(Results.Ok), statuses = Seq("foo","bar"))

        val response = await(result)
        response.header.status mustBe SEE_OTHER


    }

    "checkStatus should return OK if status is draft" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "draft"))
        val result = SessionRegistration.checkStatus(f = redirect =>
            Future.successful(Results.Ok))

        val response = await(result)

        response.header.status mustBe OK


    }

    "checkStatus should redirect to dashboard if status is not draft or locked" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "foo"))
        val result = SessionRegistration.checkStatus(f = redirect =>
            Future.successful(Results.Ok))
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.dashboard.routes.DashboardController.show.toString)
    }

    "checkStatus should redirect to post-sign-in if status is locked" in {
        mockKeystoreFetchAndGet("registrationID", Some("1"))
        CTRegistrationConnectorMocks.retrieveCTRegistration(buildCorporationTaxModel(status = "locked"))
        val result = SessionRegistration.checkStatus(f = redirect =>
            Future.successful(Results.Ok))

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(controllers.reg.routes.SignInOutController.postSignIn(None).toString)
    }
}