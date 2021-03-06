/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import config.FrontendAppConfig
import controllers.reg.QuestionnaireController
import mocks.{MetricServiceMock, SCRSMocks}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{MetricsService, QuestionnaireService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class QuestionnaireControllerSpec extends UnitSpec with GuiceOneAppPerSuite with MockitoSugar with SCRSMocks {

  val mockQuestionnaireService = mock[QuestionnaireService]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {
    val controller = new QuestionnaireController(mockMcc) {
      override val messagesApi = app.injector.instanceOf[MessagesApi]
      override val metricsService: MetricsService = MetricServiceMock
      override val qService = mockQuestionnaireService
      override val appConfig: FrontendAppConfig = new FrontendAppConfig(mockConfiguration: Configuration) {
        override lazy val assetsPrefix = ""
        override lazy val reportAProblemNonJSUrl = ""
        override lazy val contactFrontendPartialBaseUrl = ""
        override lazy val reportAProblemPartialUrl = ""
        override lazy val serviceId = "SCRS"
        override lazy val corsRenewHost = Some("")
        override lazy val timeoutInSeconds = ""
        override lazy val timeoutDisplayLength = ""
        override lazy val govHostUrl: String = "govukurl"
      }
    }

  }

  "show" should {
    "display the questionnaire page" in new Setup {
      val result = await(controller.show(FakeRequest()))
      status(result) shouldBe 200
    }
  }

  "submit" should {

    "redirect to post sign in on successful form submission with all fields populated" in new Setup {
      when(
        mockQuestionnaireService.sendAuditEventOnSuccessfulSubmission(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[Request[AnyContent]])).
        thenReturn(Future.successful(AuditResult.Success))

      val form = Map(
        "ableToAchieve" -> "No",
        "whyNotAchieve" -> "because",
        "meetNeeds" -> "1",
        "tryingToDo" -> "RegisterCompany",
        "satisfaction" -> "very satisfied",
        "recommendation" -> "foo",
        "improvements" -> "improvements here"
      )

      val request = FakeRequest().withFormUrlEncodedBody(form.toSeq: _*)

      val result = await(controller.submit(request))
      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("govukurl")
    }

    "return a bad request on an unsuccessful form submission" in new Setup {
      when(
        mockQuestionnaireService.sendAuditEventOnSuccessfulSubmission(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[Request[AnyContent]])).
        thenReturn(Future.successful(AuditResult.Success))

      val form = Map(
        "ableToAchieve" -> "",
        "whyNotAchieve" -> "because",
        "meetNeeds" -> "",
        "tryingToDo" -> "",
        "satisfaction" -> "",
        "recommendation" -> "",
        "improvements" -> "improvements here"
      )

      val request = FakeRequest().withFormUrlEncodedBody(form.toSeq: _*)

      val result = await(controller.submit(request))
      status(result) shouldBe 400
    }

    "not be affected by a failed audit event" in new Setup {

      when(
        mockQuestionnaireService.sendAuditEventOnSuccessfulSubmission(ArgumentMatchers.any())(ArgumentMatchers.any[HeaderCarrier], ArgumentMatchers.any[Request[AnyContent]])).
        thenReturn(Future.successful(AuditResult.Failure("")))

      val form = Map(
        "ableToAchieve" -> "No",
        "whyNotAchieve" -> "because",
        "meetNeeds" -> "1",
        "tryingToDo" -> "RegisterCompany",
        "satisfaction" -> "very satisfied",
        "recommendation" -> "foo",
        "improvements" -> "improvements here"
      )

      val request = FakeRequest().withFormUrlEncodedBody(form.toSeq: _*)

      val result = await(controller.submit(request))
      status(result) shouldBe 303
    }

  }

}
