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
import controllers.takeovers.OtherBusinessAddressController._
import fixtures.LoginFixture
import forms.takeovers.OtherBusinessAddressForm
import forms.takeovers.OtherBusinessAddressForm._
import helpers.SCRSSpec
import mocks.{AddressLookupFrontendServiceMock, AddressPrepopulationServiceMock, BusinessRegConnectorMock, TakeoverServiceMock}
import models.takeovers.PreselectedAddress
import models.{NewAddress, TakeoverDetails}
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import views.html.takeovers.OtherBusinessAddress

import scala.concurrent.Future

class OtherBusinessAddressControllerSpec(implicit lang: Lang) extends SCRSSpec
  with GuiceOneAppPerSuite
  with LoginFixture
  with AuthBuilder
  with TakeoverServiceMock
  with AddressLookupFrontendServiceMock
  with BusinessRegConnectorMock
  with AddressPrepopulationServiceMock
  with I18nSupport {

  val testBusinessName: String = "testName"
  val testRegistrationId: String = "testRegistrationId"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  implicit val request: Request[AnyContent] = FakeRequest()
  implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit val messages: Messages = app.injector.instanceOf[Messages]
  implicit val langs = app.injector.instanceOf[Langs]
  val mockMcc = app.injector.instanceOf[MessagesControllerComponents]
  val page = app.injector.instanceOf[OtherBusinessAddress]
  val mockControllerErrorHandler = app.injector.instanceOf[ControllerErrorHandler]

  object TestOtherBusinessAddressController extends OtherBusinessAddressController(
    mockAuthConnector,
    mockTakeoverService,
    mockAddressPrepopulationService,
    mockAddressLookupFrontendService,
    mockCompanyRegistrationConnector,
    mockBusinessRegConnector,
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
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockGetTakeoverDetails(testRegistrationId)(Future.successful(None))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url)
        }
      }

      "the user indicated they are not doing a takeover" should {
        "return 303 with a redirect to accounting dates controller" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = false)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show.url)
        }
      }

      "the user has not submitted a business name before" should {
        "return 303 with a redirect to other business name page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(replacingAnotherBusiness = true, None)
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.OtherBusinessNameController.show.url)
        }
      }

      "the user has not submitted a business address before" should {
        "return 200 with the other business address page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress)))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe page(OtherBusinessAddressForm.form(testBusinessName, 1), testBusinessName, Seq(testBusinessAddress)).body
          session(res).get(addressSeqKey) should contain(Json.toJson(Seq(testBusinessAddress)).toString())
        }
      }

      "the user has previously submitted a business address" should {
        "return 200 with the other business address page prepopulated with the previously selected address" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testOldBusinessAddress: NewAddress = testBusinessAddress.copy(addressLine1 = "otherTestLine1")
          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress, testOldBusinessAddress)))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testOldBusinessAddress)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe page(
            OtherBusinessAddressForm.form(testBusinessName, 2).fill(PreselectedAddress(1)),
            testBusinessName,
            Seq(testBusinessAddress, testOldBusinessAddress)
          ).body
          session(res).get(addressSeqKey) should contain(Json.toJson(Seq(testBusinessAddress, testOldBusinessAddress)).toString())
        }
      }
      "the user has previously submitted a business address which is not in the same format as the stored address" should {
        "return 200 with the other business address page prepopulated with the previously selected address" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testOldBusinessAddress: NewAddress = testBusinessAddress.copy(addressLine1 = "otherTestLine1")

          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress, testOldBusinessAddress)))

          val testNonMatchingOldBusinessAddress: NewAddress = testOldBusinessAddress.copy(auditRef = Some("testAuditRef"))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testNonMatchingOldBusinessAddress)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestOtherBusinessAddressController.show(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe page(
            OtherBusinessAddressForm.form(testBusinessName, 2).fill(PreselectedAddress(1)),
            testBusinessName,
            Seq(testBusinessAddress, testOldBusinessAddress)
          ).body
          session(res).get(addressSeqKey) should contain(Json.toJson(Seq(testBusinessAddress, testOldBusinessAddress)).toString())
        }
      }
    }
    "the feature switch is disabled" should {
      "throw a NotFoundException" in {
        mockAuthorisedUser(Future.successful({}))
        mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        mockTakeoversFeatureSwitch(isEnabled = false)

        intercept[NotFoundException](await(TestOtherBusinessAddressController.show(request)))
      }
    }
  }

  "submit" when {
    "user is authorised with a valid reg ID" when {
      "the form contains valid data" should {
        "redirect to who agreed takeover page when the service does not fail" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessAddressKey -> "0")
              .withSession(addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString())

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress)
          )
          mockUpdateBusinessAddress(testRegistrationId, testBusinessAddress)(testTakeoverDetails)

          val res: Result = TestOtherBusinessAddressController.submit(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
          session(res).get(addressSeqKey) shouldBe None
        }
      }
      "the form contains valid data" should {
        "redirect to alf when the choice is Other" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)
          mockInitialiseAlfJourney(
            handbackLocation = controllers.takeovers.routes.OtherBusinessAddressController.handbackFromALF(None),
            specificJourneyKey = "takeovers",
            lookupPageHeading = messages("page.addressLookup.takeovers.otherBusinessAddress.lookup.heading", testBusinessName),
            confirmPageHeading = messages("page.addressLookup.takeovers.otherBusinessAddress.confirm.description", testBusinessName)
          )(Future.successful("TEST/redirectUrl"))

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessAddressKey -> "Other")
              .withSession(addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString())

          val res: Result = TestOtherBusinessAddressController.submit(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain("TEST/redirectUrl")
        }
      }
      "the form contains invalid data" should {
        "return a bad request and update the page with errors" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(otherBusinessAddressKey -> "")
              .withSession(addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString())

          val res: Result = TestOtherBusinessAddressController.submit(request)

          status(res) shouldBe BAD_REQUEST
          Jsoup.parse(bodyOf(res))
            .getElementById("otherBusinessAddress-error-summary").text shouldBe s"Tell us $testBusinessName’s address"
        }
      }
    }
  }

  "handbackFromALF" when {
    "user is authorised with a valid reg ID" when {
      "the handback comes back with a valid address" should {
        "redirect to who agreed takeover page when the service does not fail" in {
          val testAlfId = "testAlfId"

          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)
          mockGetAddress(id = testAlfId)(Future.successful(testBusinessAddress))

          implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress)
          )

          mockUpdateBusinessAddress(testRegistrationId, testBusinessAddress)(testTakeoverDetails)

          mockUpdatePrePopAddress(testRegistrationId, testBusinessAddress)(Future.successful(true))

          val res: Result = TestOtherBusinessAddressController.handbackFromALF(Some(testAlfId))(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
          session(res).get(addressSeqKey) shouldBe None
        }
      }
    }
  }
}
