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

package controllers.takeovers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import builders.AuthBuilder
import controllers.reg.ControllerErrorHandler
import fixtures.{AccountingDatesFixture, AccountingDetailsFixture, LoginFixture}
import forms.takeovers.OtherBusinessNameForm
import forms.takeovers.OtherBusinessNameForm.otherBusinessNameKey
import helpers.SCRSSpec
import mocks.TakeoverServiceMock
import models.TakeoverDetails
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import views.html.takeovers.OtherBusinessName

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OtherBusinessNameControllerSpec extends SCRSSpec with GuiceOneAppPerSuite with AccountingDatesFixture with AccountingDetailsFixture
  with LoginFixture with AuthBuilder with TakeoverServiceMock with I18nSupport {
  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]

  class Setup {
    val testBusinessName: String = "testName"
    val testRegistrationId = "testRegistrationId"
    implicit val request: Request[AnyContent] = FakeRequest()
    implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
    implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
    val page = app.injector.instanceOf[OtherBusinessName]
    val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]



    object TestOtherBusinessNameController extends OtherBusinessNameController(
      mockAuthConnector,
      mockTakeoverService,
      mockCompanyRegistrationConnector,
      mockKeystoreConnector,
      mockSCRSFeatureSwitches,
      mockMcc,
      mockControllerErrorHandler,
      page
    )

  }

  "show" when {
    "user is authorised with a valid reg ID and the feature switch is enabled" when {
      "the user does not any associated TakeoverDetails" should {
        "return 303 with a redirect to replacing another business controller" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val res: Result = await(TestOtherBusinessNameController.show(request))

          status(res) mustBe SEE_OTHER
          redirectLocation(res) must contain(controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url)
        }
      }

      "the user indicated they are not doing a takeover" should {
        "return 303 with a redirect to accounting dates controller" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = await(TestOtherBusinessNameController.show(request))

          status(res) mustBe SEE_OTHER
          redirectLocation(res) must contain(controllers.reg.routes.AccountingDatesController.show.url)
        }
      }

      "the user has not submitted a business name before" should {
        "return 200 with the other business name page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, None)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = await(TestOtherBusinessNameController.show(request))

          status(res) mustBe OK
          bodyOf(res) mustBe page(OtherBusinessNameForm.form).body
        }
      }

      "the user has previously submitted a business name" should {
        "return 200 with the other business name page" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName))
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = await(TestOtherBusinessNameController.show(request))

          status(res) mustBe OK
          bodyOf(res) mustBe page(OtherBusinessNameForm.form.fill(testBusinessName)).body
        }
      }
    }
  }

  "submit" when {
    "user is authorised with a valid reg ID" when {
      "the form contains valid data" should {
        "redirect to company address when the service does not fail" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessNameKey -> testBusinessName)

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, Some(testBusinessName))
          mockUpdateBusinessName(testRegistrationId, testBusinessName)(Future.successful(testTakeoverDetails))

          val res: Result = await(TestOtherBusinessNameController.submit(request))

          status(res) mustBe SEE_OTHER
          redirectLocation(res) must contain(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
        }
      }

      "the form contains invalid data" should {
        "return a bad request and update the page with errors if name does not pass validation" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          override val testBusinessName: String = ""
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessNameKey -> testBusinessName)

          val res: Result = await(TestOtherBusinessNameController.submit(request))

          status(res) mustBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("otherBusinessName-error").text mustBe "Tell us the name of the other business"
        }

        "return a bad request and update the page with errors if name is invalid" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          override val testBusinessName: String = "test©Æф"
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessNameKey -> testBusinessName)

          val res: Result = await(TestOtherBusinessNameController.submit(request))

          status(res) mustBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("otherBusinessName-error").text mustBe "Business name must not include ©, Æ and ф"
        }

        "return a bad request and update the page with errors if name is too long" in new Setup {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          override val testBusinessName: String = "01234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
          override implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessNameKey -> testBusinessName)

          val res: Result = await(TestOtherBusinessNameController.submit(request))

          status(res) mustBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("otherBusinessName-error").text mustBe "Enter the name using 100 characters or less"
        }
      }
    }
  }
}
