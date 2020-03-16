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
import controllers.takeovers.OtherBusinessAddressController._
import fixtures.LoginFixture
import forms.takeovers.HomeAddressForm
import forms.takeovers.HomeAddressForm._
import helpers.SCRSSpec
import mocks.{AddressLookupFrontendServiceMock, AddressPrepopulationServiceMock, BusinessRegConnectorMock, TakeoverServiceMock}
import models.takeovers.PreselectedAddress
import models.{NewAddress, TakeoverDetails}
import org.jsoup.Jsoup
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.test.WithFakeApplication
import views.html.takeovers.HomeAddress

import scala.concurrent.Future

class PreviousOwnersAddressControllerSpec extends SCRSSpec
  with WithFakeApplication
  with LoginFixture
  with AuthBuilder
  with TakeoverServiceMock
  with AddressLookupFrontendServiceMock
  with BusinessRegConnectorMock
  with AddressPrepopulationServiceMock
  with I18nSupport {

  val testBusinessName: String = "testName"
  val testPreviousOwnersName: String = "testName"
  val testRegistrationId: String = "testRegistrationId"
  val testBusinessAddress: NewAddress = NewAddress("BusinessAddressLine1", "BusinessAddressLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testPreviousOwnersAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  implicit val request: Request[AnyContent] = FakeRequest()
  implicit val actorSystem: ActorSystem = ActorSystem("MyTest")
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]

  object TestPreviousOwnersAddressController extends PreviousOwnersAddressController(
    mockAuthConnector,
    mockTakeoverService,
    mockAddressPrepopulationService,
    mockAddressLookupFrontendService,
    mockCompanyRegistrationConnector,
    mockBusinessRegConnector,
    mockKeystoreConnector,
    mockSCRSFeatureSwitches
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

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.ReplacingAnotherBusinessController.show().url)
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

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show().url)
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

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.OtherBusinessNameController.show().url)
        }
      }

      "the user has not submitted a business address before" should {
        "return 303 with the other business address page" in {
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

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.OtherBusinessAddressController.show().url)
        }
      }

      "the user has not submitted a previous owners name before" should {
        "return 303 with the previous owners name page" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress)))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show().url)
        }
      }

      "the user has previously submitted a previous owners address" should {
        "return 200 with the previous owners address page prepopulated with the previously selected address" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testOldPreviousOwnersAddress: NewAddress = testPreviousOwnersAddress.copy(addressLine1 = "otherTestLine1")
          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress, testOldPreviousOwnersAddress)))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress),
            Some(testPreviousOwnersName),
            Some(testOldPreviousOwnersAddress)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe HomeAddress(
            HomeAddressForm.form(2).fill(PreselectedAddress(1)),
            testPreviousOwnersName,
            Seq(testBusinessAddress, testOldPreviousOwnersAddress)
          ).body
          session(res).get(addressSeqKey) should contain(Json.toJson(Seq(testBusinessAddress, testOldPreviousOwnersAddress)).toString())
        }
      }

      "the user has previously submitted a previous owners address which is not in the same format as the stored address" should {
        "return 200 with the previous ownerss address page prepopulated with the previously selected address" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          mockTakeoversFeatureSwitch(isEnabled = true)
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))

          val testOldPreviousOwnersAddress: NewAddress = testPreviousOwnersAddress.copy(addressLine1 = "otherTestLine1")

          mockRetrieveAddresses(testRegistrationId)(Future.successful(Seq(testBusinessAddress, testOldPreviousOwnersAddress)))

          val testNonMatchingAddress: NewAddress = testOldPreviousOwnersAddress.copy(auditRef = Some("testAuditRef"))

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress),
            Some(testPreviousOwnersName),
            Some(testNonMatchingAddress)
          )
          mockGetTakeoverDetails(testRegistrationId)(Future.successful(Some(testTakeoverDetails)))

          val res: Result = TestPreviousOwnersAddressController.show()(request)

          status(res) shouldBe OK
          bodyOf(res) shouldBe HomeAddress(
            HomeAddressForm.form(2).fill(PreselectedAddress(1)),
            testPreviousOwnersName,
            Seq(testBusinessAddress, testOldPreviousOwnersAddress)
          ).body
          session(res).get(addressSeqKey) should contain(Json.toJson(Seq(testBusinessAddress, testOldPreviousOwnersAddress)).toString())
        }
      }
    }

    "the feature switch is disabled" should {
      "throw a NotFoundException" in {
        mockAuthorisedUser(Future.successful({}))
        mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
        CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
        mockTakeoversFeatureSwitch(isEnabled = false)

        intercept[NotFoundException](await(TestPreviousOwnersAddressController.show()(request)))
      }
    }
  }

  "submit" when {
    "user is authorised with a valid reg ID" when {
      "the form contains valid data" should {
        "redirect to accounting dates page when submission is successful" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(homeAddressKey -> "0")
              .withSession(addressSeqKey -> Json.toJson(Seq(testPreviousOwnersAddress)).toString())

          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
            replacingAnotherBusiness = true,
            Some(testBusinessName),
            Some(testBusinessAddress),
            Some(testPreviousOwnersName)
          )
          mockUpdatePreviousOwnersAddress(testRegistrationId, testPreviousOwnersAddress)(testTakeoverDetails)

          val res: Result = TestPreviousOwnersAddressController.submit()(request)

          status(res) shouldBe SEE_OTHER
          redirectLocation(res) should contain(controllers.reg.routes.AccountingDatesController.show().url)
          session(res).get(addressSeqKey) shouldBe None
        }
      }

//      "the form contains valid data" should {
//        "redirect to alf when the choice is Other" in {
//          mockAuthorisedUser(Future.successful({}))
//          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
//          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
//          mockTakeoversFeatureSwitch(isEnabled = true)
//          mockInitialiseAlfJourney(
//            handbackLocation = controllers.takeovers.routes.OtherBusinessAddressController.handbackFromALF(),
//            specificJourneyKey = "takeovers",
//            lookupPageHeading = messagesApi("page.addressLookup.takeovers.otherBusinessAddress.lookup.heading", testBusinessName),
//            confirmPageHeading = messagesApi("page.addressLookup.takeovers.otherBusinessAddress.confirm.description", testBusinessName)
//          )(Future.successful("TEST/redirectUrl"))
//
//          implicit val request: Request[AnyContentAsFormUrlEncoded] =
//            FakeRequest().withFormUrlEncodedBody(otherBusinessAddressKey -> "Other")
//              .withSession(addressSeqKey -> Json.toJson(Seq(testBusinessAddress)).toString())
//
//          val res: Result = TestPreviousOwnersAddressController.submit()(request)
//
//          status(res) shouldBe SEE_OTHER
//          redirectLocation(res) should contain("TEST/redirectUrl")
//        }
//      } TODO fix ALF tests when adding ALF journey

      "the form contains invalid data" should {
        "return a bad request and update the page with errors" in {
          mockAuthorisedUser(Future.successful({}))
          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
          mockTakeoversFeatureSwitch(isEnabled = true)

          implicit val request: Request[AnyContentAsFormUrlEncoded] =
            FakeRequest().withFormUrlEncodedBody(homeAddressKey -> "")
              .withSession(addressSeqKey -> Json.toJson(Seq(testPreviousOwnersAddress)).toString())

          val res: Result = TestPreviousOwnersAddressController.submit()(request)

          status(res) shouldBe BAD_REQUEST
          Jsoup.parse(bodyOf(res)).getElementById("homeAddress-error-summary").text shouldBe "Tell us their home address"
        }
      }
    }
  }

//  "handbackFromALF" when {
//    "user is authorised with a valid reg ID" when {
//      "the handback comes back with a valid address" should {
//        "redirect to who agreed takeover page when the service does not fail" in {
//          mockAuthorisedUser(Future.successful({}))
//          mockKeystoreFetchAndGet("registrationID", Some(testRegistrationId))
//          CTRegistrationConnectorMocks.retrieveCTRegistration(cTDoc("draft", ""))
//          mockTakeoversFeatureSwitch(isEnabled = true)
//          mockGetAddress(Future.successful(testBusinessAddress))
//
//          implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
//
//          val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
//            replacingAnotherBusiness = true,
//            Some(testBusinessName),
//            Some(testBusinessAddress)
//          )
//
//          mockUpdateBusinessAddress(testRegistrationId, testBusinessAddress)(testTakeoverDetails)
//
//          mockUpdatePrePopAddress(testRegistrationId, testBusinessAddress)(Future.successful(true))
//
//          val res: Result = TestPreviousOwnersAddressController.handbackFromALF()(request)
//
//          status(res) shouldBe SEE_OTHER
//          redirectLocation(res) should contain(controllers.takeovers.routes.WhoAgreedTakeoverController.show().url)
//          session(res).get(addressSeqKey) shouldBe None
//        }
//      }
//    }
//  } Todo Fix ALF tests when adding ALF journey
}
