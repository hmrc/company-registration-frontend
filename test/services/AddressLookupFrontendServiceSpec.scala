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

package services

import connectors.AddressLookupConnector
import fixtures.AddressFixture
import mocks.SCRSMocks
import models._
import org.mockito.Matchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future

class AddressLookupFrontendServiceSpec extends UnitSpec with MockitoSugar with AddressFixture with WithFakeApplication with SCRSMocks {


  class Setup {
    val service = new AddressLookupFrontendService {
      override val addressLookupFrontendConnector: AddressLookupConnector = mockAddressLookupConnector
      override val metricsService: MetricsService = mockMetricsService
      override val addressLookupFrontendURL = "testAddressLookupUrl"
      override val companyRegistrationFrontendURL = "testCompanyRegUrl"
      override val timeoutInSeconds= 22666
      override val messagesApi = fakeApplication.injector.instanceOf[MessagesApi]
      override val groupService: GroupService = mockGroupService
    }
  }

  implicit val hc = HeaderCarrier()

  "getAddress" should {

    val id = "testID"
    val address = validNewAddress

    "return an address" in new Setup {

      when(mockAddressLookupConnector.getAddress(eqTo(id))(any()))
        .thenReturn(Future.successful(address))

      val req = FakeRequest("GET", s"/test-uri?id=$id")
      val result = await(service.getAddress(hc, req))

      result shouldBe address
    }

    "throw a QueryStringMissingException" in new Setup {
      val req = FakeRequest("GET", "/test-uri")

      intercept[QueryStringMissingException](await(service.getAddress(hc, req)))
    }
  }

  "buildAddressLookupUrl" should {

    val id = "testID"
    val timeoutUrl = "/test/timeout/url"
    val req = FakeRequest("GET", "/test-uri")
    val call = Call("testUrl", "")

    "return an address" in new Setup {
      when(mockAddressLookupConnector.getOnRampURL(any[JsObject])(any[HeaderCarrier]()))
        .thenReturn(Future.successful(id))

      await(service.buildAddressLookupUrl(call, "foo")) shouldBe id
    }
  }
  "messageKey" should {
    val mockMApi = mock[MessagesApi]
    "return the probable key (non page specific first)" in new Setup {
      when(mockMApi.isDefinedAt(any())(any())).thenReturn(true)
      val res = service.messageKey("foo",mockMApi)("bar")
      res shouldBe "page.addressLookup.bar"
    }
    "return the potential key if probably key doesnt exist" in new Setup {
      when(mockMApi.isDefinedAt(any())(any())).thenReturn(false)
      val res = service.messageKey("foo",mockMApi)("bar")
      res shouldBe "page.addressLookup.foo.bar"
    }
  }
  "initForPage" should {
    "return jsObject for ALF Setup" in new Setup {
      val resOfJson =  service.initForPage("foo", Call("GET","/foo"),"PPOB")
      resOfJson shouldBe Json.parse(
        """
          |{"continueUrl":"testCompanyRegUrl/foo",
          |"homeNavHref":"http://www.hmrc.gov.uk/",
          |"navTitle":"Set up a limited company and register for Corporation Tax",
          |"showPhaseBanner":true,"alphaPhase": false,
          |"phaseBannerHtml":"This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
          |"includeHMRCBranding":false,
          |"showBackButtons":true,
          |"deskProServiceName":"SCRS",
          |"lookupPage":{"title":"Find the address","heading":"Find the address where the company will carry out most of its business activities","filterLabel":"Property name or number","submitLabel":"Find address","manualAddressLinkText":"Enter address manually"},
          |"selectPage":{"title":"Choose an address","heading":"Choose an address","proposalListLimit":30,"showSearchAgainLink":true,"searchAgainLinkText":"Search again","editAddressLinkText":"The address is not on the list"},"editPage":{"title":"Enter an address","heading":"Enter an address","line1Label":"Address line 1","line2Label":"Address line 2","line3Label":"Address line 3","showSearchAgainLink":true},
          |"confirmPage":{"title":"Confirm the address","heading":"Confirm where the company will carry out most of its business activities","showSubHeadingAndInfo":false,"submitLabel":"Confirm and continue","showSearchAgainLink":false,"showChangeLink":true,"changeLinkText":"Change"},
          |"timeout":{"timeoutAmount":22666,"timeoutUrl":"testCompanyRegUrlfoo"}}
        """.stripMargin)
    }
  }
  "initForGroups" should {
    "return json with group names in lookup and confirm titles" in new Setup {
      val validGroups = Groups(true,
        nameOfCompany = Some(GroupCompanyName("foo bar","Other")),
        addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("country A"),Some("ZZ1 1ZZ")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(validGroups)))
     val res = service.initForGroups("1","foo", Call("GET","/foo"))
      await(res) shouldBe Json.parse(
        """
          |{"continueUrl":"testCompanyRegUrl/foo",
          |"homeNavHref":"http://www.hmrc.gov.uk/",
          |"navTitle":"Set up a limited company and register for Corporation Tax",
          |"showPhaseBanner":true,"alphaPhase":false,
          |"phaseBannerHtml":"This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
          |"includeHMRCBranding":false,
          |"showBackButtons":true,
          |"deskProServiceName":"SCRS",
          |"lookupPage":{"title":"Find the address","heading":"Find foo bar's registered office address","filterLabel":"Property name or number","submitLabel":"Find address","manualAddressLinkText":"Enter address manually"},
          |"selectPage":{"title":"Choose an address","heading":"Choose an address","proposalListLimit":30,"showSearchAgainLink":true,"searchAgainLinkText":"Search again","editAddressLinkText":"The address is not on the list"},"editPage":{"title":"Enter an address","heading":"Enter an address","line1Label":"Address line 1","line2Label":"Address line 2","line3Label":"Address line 3","showSearchAgainLink":true},
          |"confirmPage":{"title":"Confirm the address","heading":"Confirm foo bar's registered office address","showSubHeadingAndInfo":false,"submitLabel":"Confirm and continue","showSearchAgainLink":false,"showChangeLink":true,"changeLinkText":"Change"},
          |"timeout":{"timeoutAmount":22666,"timeoutUrl":"testCompanyRegUrlfoo"}}
        """.stripMargin)
    }
    "throw an exception if groups block does not exist" in new Setup {
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(None))
      intercept[Exception](await(service.initForGroups("1","foo", Call("GET","/foo"))))
    }
    "throw an exception if name block does not exist" in new Setup {
      when(mockGroupService.retrieveGroups(any())(any())).thenReturn(Future.successful(Some(Groups(true,None,None,None))))
      intercept[Exception](await(service.initForGroups("1","foo", Call("GET","/foo"))))
    }
  }

  "initconfig" should {
    "return jsObject for ALF setup with customizable signout and call" in new Setup {
      val resOfJson =  service.initConfig("foo", Call("GET","/foo"))
      resOfJson.deepMerge shouldBe Json.parse(
        """
          |{"continueUrl":"testCompanyRegUrl/foo",
          |"homeNavHref":"http://www.hmrc.gov.uk/",
          |"navTitle":"Set up a limited company and register for Corporation Tax",
          |"showPhaseBanner":true,"alphaPhase":false,
          |"phaseBannerHtml":"This is a new service. Help us improve it - send your <a href='https://www.tax.service.gov.uk/register-for-paye/feedback'>feedback</a>.",
          |"includeHMRCBranding":false,
          |"showBackButtons":true,
          |"deskProServiceName":"SCRS",
          |"lookupPage":{"title":"Find the address","heading":"Find the address where the company will carry out most of its business activities","filterLabel":"Property name or number","submitLabel":"Find address","manualAddressLinkText":"Enter address manually"},
          |"selectPage":{"title":"Choose an address","heading":"Choose an address","proposalListLimit":30,"showSearchAgainLink":true,"searchAgainLinkText":"Search again","editAddressLinkText":"The address is not on the list"},"editPage":{"title":"Enter an address","heading":"Enter an address","line1Label":"Address line 1","line2Label":"Address line 2","line3Label":"Address line 3","showSearchAgainLink":true},
          |"confirmPage":{"title":"Confirm the address","heading":"Confirm where the company will carry out most of its business activities","showSubHeadingAndInfo":false,"submitLabel":"Confirm and continue","showSearchAgainLink":false,"showChangeLink":true,"changeLinkText":"Change"},
          |"timeout":{"timeoutAmount":22666,"timeoutUrl":"testCompanyRegUrlfoo"}}
        """.stripMargin)
    }
  }
}
