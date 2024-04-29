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

package controllers.takeovers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ActorMaterializer, Materializer}
import builders.AuthBuilder
import config.AppConfig
import controllers.reg.ControllerErrorHandler
import fixtures.{AccountingDatesFixture, AccountingDetailsFixture, LoginFixture}
import forms.takeovers.ReplacingAnotherBusinessForm
import forms.takeovers.ReplacingAnotherBusinessForm.replacingAnotherBusinessKey
import helpers.SCRSSpec
import mocks.TakeoverServiceMock
import models.TakeoverDetails
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import views.html.takeovers.ReplacingAnotherBusiness

import scala.concurrent.{ExecutionContext, Future}

class ReplacingAnotherBusinessControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AccountingDatesFixture with AccountingDetailsFixture
  with LoginFixture with AuthBuilder with TakeoverServiceMock with I18nSupport {
  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  lazy implicit val appConfig = app.injector.instanceOf[AppConfig]

  class Setup {
    val testRegistrationId = "testRegistrationId"
    implicit val request: Request[AnyContent] = FakeRequest()
    implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
    implicit val actorMaterializer: Materializer = Materializer(actorSystem)
    val page = app.injector.instanceOf[ReplacingAnotherBusiness]
    val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]




    object TestReplacingAnotherBusinessController extends ReplacingAnotherBusinessController(
      mockAuthConnector,
      mockTakeoverService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockSCRSFeatureSwitches,
      mockMcc,
      mockControllerErrorHandler,
      page
    )(appConfig, ec)

  }

  "show" when {
    "user is authorised with a valid reg ID and the feature switch is enabled" when {
      "the user has not submitted takeover information before" should {
        "return 200 with the replacing another business page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val res: Result = await(TestReplacingAnotherBusinessController.show(request))

          status(res) mustBe OK
          bodyOf(res) mustBe page(ReplacingAnotherBusinessForm.form).body

        }
      }

      "the user has previously submitted takeover information" should {
        "return 200 with the replacing another business page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = await(TestReplacingAnotherBusinessController.show(request))

          status(res) mustBe OK
          bodyOf(res) mustBe page(ReplacingAnotherBusinessForm.form.fill(testTakeoverDetails.replacingAnotherBusiness)).body

        }
      }
    }
  }
  "submit" when {
    "user is authorised with a valid reg ID and the feature switch is enabled" when {
      "the form contains valid data" should {
        "store the selected answer and redirect the user to Accounting Dates" in new Setup {
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(replacingAnotherBusinessKey -> false.toString)

          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
          mockUpdateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = false)(Future.successful(testTakeoverDetails))

          val res: Result = await(TestReplacingAnotherBusinessController.submit(request))

          status(res) mustBe SEE_OTHER
          redirectLocation(res) must contain(controllers.reg.routes.AccountingDatesController.show.url)
        }
        "store the selected answer and redirect the user to the next Takeover Page" in new Setup {
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(replacingAnotherBusinessKey -> true.toString)

          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
          mockUpdateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = true)(Future.successful(testTakeoverDetails))

          val res: Result = await(TestReplacingAnotherBusinessController.submit(request))

          status(res) mustBe SEE_OTHER
          redirectLocation(res) must contain(controllers.takeovers.routes.OtherBusinessNameController.show.url)
        }
      }
    }
  }
}
