/*
 * Copyright 2020 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import builders.AuthBuilder
import fixtures.{AccountingDatesFixture, AccountingDetailsFixture, LoginFixture}
import forms.takeovers.ReplacingAnotherBusinessForm
import forms.takeovers.ReplacingAnotherBusinessForm.replacingAnotherBusinessKey
import helpers.SCRSSpec
import mocks.TakeoverServiceMock
import models.TakeoverDetails
import org.mockito.Mockito._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AnyContent, AnyContentAsFormUrlEncoded, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.test.WithFakeApplication
import utils.BooleanFeatureSwitch
import views.html.takeovers.ReplacingAnotherBusiness

import scala.concurrent.Future

class ReplacingAnotherBusinessControllerSpec extends SCRSSpec with WithFakeApplication with AccountingDatesFixture with AccountingDetailsFixture
  with LoginFixture with AuthBuilder with TakeoverServiceMock with I18nSupport {
  implicit lazy val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]

  def mockTakeoversFeatureSwitch(isEnabled: Boolean): Unit =
    when(mockSCRSFeatureSwitches.takeovers).thenReturn(BooleanFeatureSwitch("takeoverFeatureSwitch", isEnabled))

  class Setup {
    val testRegistrationId = "testRegistrationId"
    implicit val request: Request[AnyContent] = FakeRequest()
    implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()


    object TestReplacingAnotherBusinessController extends ReplacingAnotherBusinessController(
      mockAuthConnector,
      mockTakeoverService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockSCRSFeatureSwitches,
      messagesApi
    )

  }

  "show" when {
    "user is authorised with a valid reg ID and the feature switch is enabled" when {
      "the user has not submitted takeover information before" should {
        "return 200 with the replacing another business page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val res: Result = await(TestReplacingAnotherBusinessController.show()(request))

          status(res) shouldBe OK
          bodyOf(res) shouldBe ReplacingAnotherBusiness(ReplacingAnotherBusinessForm.form).body

        }
      }

      "the user has previously submitted takeover information" should {
        "return 200 with the replacing another business page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = await(TestReplacingAnotherBusinessController.show()(request))

          status(res) shouldBe OK
          bodyOf(res) shouldBe ReplacingAnotherBusiness(ReplacingAnotherBusinessForm.form.fill(testTakeoverDetails.replacingAnotherBusiness)).body

        }
      }
    }
    "the feature switch is disabled" should {
      "throw a NotFoundException" in new Setup {
        mockAuthorisedUser(Future.successful({}))
        mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        mockTakeoversFeatureSwitch(isEnabled = false)

        intercept[NotFoundException](await(TestReplacingAnotherBusinessController.show()(request)))
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

          val res: Result = await(TestReplacingAnotherBusinessController.submit()(request))

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show().url)
        }
        //TODO Update when the next page is ready
        "store the selected answer and redirect the user to the next Takeover Page" in new Setup {
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(replacingAnotherBusinessKey -> true.toString)

          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true)
          mockUpdateReplacingAnotherBusiness(testRegistrationId, replacingAnotherBusiness = true)(Future.successful(testTakeoverDetails))

          val res: Result = await(TestReplacingAnotherBusinessController.submit()(request))

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show().url) //TODO Update this to next takeover page when ready
        }
      }
    }
  }
}
