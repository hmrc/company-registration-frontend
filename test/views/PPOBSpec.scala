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

package views

import _root_.connectors.BusinessRegistrationConnector
import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.reg.PPOBController
import models._
import fixtures.PPOBFixture
import mocks.navModelRepoMock
import org.jsoup.Jsoup
import org.mockito.Mockito._
import org.mockito.Matchers
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.test.Helpers._
import services.AddressLookupFrontendService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.test.WithFakeApplication

import scala.concurrent.Future

class PPOBSpec extends SCRSSpec with PPOBFixture with navModelRepoMock with WithFakeApplication {
  val mockNavModelRepoObj = mockNavModelRepo
  val mockBusinessRegConnector = mock[BusinessRegistrationConnector]
  val mockAddressLookupFrontendService = mock[AddressLookupFrontendService]

  class SetupPage {
    val controller = new PPOBController {
      override val authConnector = mockAuthConnector
      override val s4LConnector = mockS4LConnector
      override val addressLookupService = mockAddressLookupService
      override val keystoreConnector = mockKeystoreConnector
      override val companyRegistrationConnector = mockCompanyRegistrationConnector
      override val pPOBService = mockPPOBService
      override val handOffService = mockHandOffService
      override val navModelMongo = mockNavModelRepoObj
      override val businessRegConnector = mockBusinessRegConnector
      override val addressLookupFrontendService = mockAddressLookupFrontendService
      implicit val messagesApi: MessagesApi = fakeApplication.injector.instanceOf[MessagesApi]
    }
  }

  "PPOBController.show" should {
    "make sure that PPOB page has the correct elements" in new SetupPage {
      CTRegistrationConnectorMocks.retrieveCTRegistration()
      mockKeystoreFetchAndGet("registrationID", Some("12345"))
      when(mockAuthConnector.getIds[UserIDs](Matchers.any())(Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[UserIDs]]()))
        .thenReturn(Future.successful(UserIDs("1", "2")))


      AuthBuilder.showWithAuthorisedUser(controller.show, mockAuthConnector) {
        result =>
          val document = Jsoup.parse(contentAsString(result))
          document.title() shouldBe "Where will the company carry out most of its business activities?"
          document.getElementById("main-heading").text shouldBe "Where will the company carry out most of its business activities?"
          document.getElementById("next").text shouldBe "Save and continue"
      }
    }
  }
}
