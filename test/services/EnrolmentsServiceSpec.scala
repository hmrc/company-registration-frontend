/*
 * Copyright 2017 HM Revenue & Customs
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

import builders.AuthBuilder
import config.{FrontendAppConfig, FrontendAuthConnector, WSHttp}
import mocks.SCRSMocks
import models.Enrolments
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import org.mockito.Mockito.when
import org.mockito.Matchers
import org.scalatest.TestData
import play.api.libs.json.{JsArray, Json}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads}

import scala.concurrent.Future

class EnrolmentsServiceSpec extends UnitSpec with WithFakeApplication with MockitoSugar with SCRSMocks {

  class Setup {
    object TestService extends EnrolmentsService {
      val authConnector = mockAuthConnector
      val http = mockWSHttp
    }
  }

  val testData = Json.parse(
    """
      |[
      |{"key":"HMCE-VATVAR-ORG","identifiers":[{"key":"VATRegNo","value":"999900449"}],"state":"Activated"},
      |{"key":"IR-CT","identifiers":[{"key":"UTR","value":"1777802586"}],"state":"Activated"},
      |{"key":"HMCE-VATDEC-ORG","identifiers":[{"key":"VATRegNo","value":"999900449"}],"state":"Activated"}
      |]""".stripMargin)

  val testUser = AuthBuilder.createTestUser

  implicit val hc = HeaderCarrier()

  "EnrolmentsService" should {
    "use the correct authConnector" in {
      EnrolmentsService.authConnector shouldBe FrontendAuthConnector
    }

    "use the correct http connector" in {
      EnrolmentsService.http shouldBe WSHttp
    }
  }

  "checkEnrolments" should {
    "return a true" when {
      "any restricted enrolments are found in the enrolments record" in new Setup {

        when(mockAuthConnector.getEnrolments[JsArray](Matchers.eq(testUser))(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[JsArray]]()))
          .thenReturn(Future.successful(testData.as[JsArray]))

        val result = await(TestService.hasBannedRegimes(testUser))
        result shouldBe true
      }
    }
  }

  "serialisation check" should {
    "get a sequence of enrolments"  in new Setup {
      val testData = Json.parse("""[{"key":"HMCE-VATVAR-ORG","identifiers":[{"key":"VATRegNo","value":"999900449"}],"state":"Activated"}]""")
      val jsArr = testData.as[JsArray]

      jsArr.value.map(_.as[Enrolments]) shouldBe Seq(Enrolments("HMCE-VATVAR-ORG", "Activated"))
    }

    "get another sequence of enrolments"  in new Setup {
      val jsArr = testData.as[JsArray]

      jsArr.value.map(_.as[Enrolments]) shouldBe Seq(Enrolments("HMCE-VATVAR-ORG", "Activated"), Enrolments("IR-CT", "Activated"), Enrolments("HMCE-VATDEC-ORG", "Activated"))
    }
  }

  "Check for enrolments in list" should {
    "not in list should return false" in new Setup {
      TestService.hasAnyEnrolments(Seq("wibble"), List("Foo", "bar")) shouldBe false
    }

    "in list should return true" in new Setup {
      TestService.hasAnyEnrolments(Seq("wibble"), List("Foo", "wibble", "bar")) shouldBe true
    }
  }
}
