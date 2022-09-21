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
import fixtures.LoginFixture
import forms.takeovers.WhoAgreedTakeoverForm
import forms.takeovers.WhoAgreedTakeoverForm._
import helpers.SCRSSpec
import mocks.TakeoverServiceMock
import models.{NewAddress, TakeoverDetails}
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.http.NotFoundException
import views.html.takeovers.WhoAgreedTakeover

import scala.concurrent.{ExecutionContext, Future}

class WhoAgreedTakeoverControllerSpec extends SCRSSpec
  with GuiceOneAppPerSuite
  with LoginFixture
  with AuthBuilder
  with TakeoverServiceMock
  with I18nSupport {

  val testBusinessName: String = "testBusinessName"
  val testRegistrationId: String = "testRegistrationId"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testPreviousOwnersName: String = "testName"
  implicit val request: Request[AnyContent] = FakeRequest()
  implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  implicit val ec: ExecutionContext = mockMcc.executionContext

  val page = app.injector.instanceOf[WhoAgreedTakeover]
  val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]


  object TestWhoAgreedTakeoverController extends WhoAgreedTakeoverController(
    mockAuthConnector,
    mockTakeoverService,
    mockCompanyRegistrationConnector,
    mockKeystoreConnector,
    mockSCRSFeatureSwitches,
    mockMcc,
    mockControllerErrorHandler,
    page
  )

  "show" when {
    "user is authorised with a valid reg ID and the feature switch is enabled" when {
      "the user does not any associated TakeoverDetails" should {
        "return 303 with a redirect to replacing another business controller" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url)
        }
      }

      "the user indicated they are not doing a takeover" should {
        "return 303 with a redirect to accounting dates controller" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(TakeoverDetails(replacingAnotherBusiness = false))))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show.url)
        }
      }

      "the user has not submitted a business name before" should {
        "return 303 with a redirect to other business name page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(TakeoverDetails(replacingAnotherBusiness = true))))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.OtherBusinessNameController.show.url)
        }
      }

      "the user has not submitted a business address before" should {
        "return 303 with a redirect to other business address page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName)
            )
          )))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
        }
      }

      "the user has not submitted a previous owners name before" should {
        "return 200 with the who agreed takeover page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress)
            )
          )))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe page(WhoAgreedTakeoverForm.form, testBusinessName).body
        }
      }

      "the user has previously submitted a previous owners name" should {
        "return 200 with the who agreed takeover page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))

          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress),
              previousOwnersName = Some(testPreviousOwnersName)
            )
          )))

          val res: Result = TestWhoAgreedTakeoverController.show(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe page(WhoAgreedTakeoverForm.form.fill(testPreviousOwnersName), testBusinessName).body
        }
      }
    }
  }

  "submit" when {
    "user is authorised with a valid reg ID" when {
      "the form contains valid data" should {
        "redirect to previous owners home address page when the service succeeds" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress)
            )
          )))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress),
            Some(testPreviousOwnersName)
          )
          mockUpdatePreviousOwnersName(testRegistrationId, testPreviousOwnersName)(testTakeoverDetails)

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(whoAgreedTakeoverKey -> testPreviousOwnersName)

          val res: Result = TestWhoAgreedTakeoverController.submit(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.PreviousOwnersAddressController.show.url)
        }
      }

      "the form contains invalid data" should {
        "return a bad request and update the page with errors if the name does not pass validation" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress)
            )
          )))

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(whoAgreedTakeoverKey -> "+")

          val res: Result = TestWhoAgreedTakeoverController.submit(request)

          status(res) shouldBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("whoAgreedTakeover-error").text shouldBe "Enter the name using only letters, numbers, spaces, hyphens and apostrophes"
        }
        "return a bad request and update the page with errors if the name is empty" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress)
            )
          )))

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(whoAgreedTakeoverKey -> "")

          val res: Result = TestWhoAgreedTakeoverController.submit(request)

          status(res) shouldBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("whoAgreedTakeover-error").text shouldBe "Tell us who agreed the takeover"
        }

        "return a bad request and update the page with errors if the name is too long" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))


          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(
            TakeoverDetails(
              replacingAnotherBusiness = true,
              businessName = Some(testBusinessName),
              businessTakeoverAddress = Some(testBusinessAddress)
            )
          )))

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(whoAgreedTakeoverKey -> "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901")

          val res: Result = TestWhoAgreedTakeoverController.submit(request)

          status(res) shouldBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("whoAgreedTakeover-error").text shouldBe "Enter the name using 100 characters or less"
        }
      }
    }
  }
}
