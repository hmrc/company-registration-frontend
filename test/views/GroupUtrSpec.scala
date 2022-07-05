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

package views

import _root_.helpers.SCRSSpec
import builders.AuthBuilder
import controllers.groups.GroupUtrController
import controllers.reg.ControllerErrorHandler
import fixtures.UserDetailsFixture
import models.{NewAddress, _}
import org.jsoup.Jsoup
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.groups.GroupUtrView

import scala.concurrent.Future

class GroupUtrSpec extends SCRSSpec with UserDetailsFixture
  with GuiceOneAppPerSuite with AuthBuilder {

  val testRegId = "1"

  class Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    val controller = new GroupUtrController(
      mockAuthConnector,
      mockKeystoreConnector,
      mockGroupService,
      mockCompanyRegistrationConnector,
      app.injector.instanceOf[MessagesControllerComponents],
      app.injector.instanceOf[GroupUtrView]
    )

    case class funcMatcher(func: Groups => Future[Result]) extends ArgumentMatcher[Groups => Future[Result]] {
      override def matches(argument: Groups => Future[Result]): Boolean = true
    }


    val ctDocFirstTimeThrough: JsValue =
      Json.parse(
        s"""
           |{
           |    "OID" : "123456789",
           |    "registrationID" : "1",
           |    "status" : "draft",
           |    "formCreationTimestamp" : "2016-10-25T12:20:45Z",
           |    "language" : "en",
           |    "verifiedEmail" : {
           |        "address" : "user@test.com",
           |        "type" : "GG",
           |        "link-sent" : true,
           |        "verified" : true,
           |        "return-link-email-sent" : false
           |    }
           |}""".stripMargin)
  }

  "show" should {
    "display the Group UTR page with no UTR pre-popped if no UTR in CR (first time through)" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)
      mockKeystoreFetchAndGet("registrationID", Some(testRegId))
      CTRegistrationConnectorMocks.retrieveCTRegistration(ctDocFirstTimeThrough)
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?")
          document.getElementsByTag("h1").first().text() shouldBe "Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?"
          document.getElementById("utr").attr("value") shouldBe ""
          document.getElementsByTag("legend").text() shouldBe "Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?"
      }
    }

    "display the Group UTR page with the UTR pre-popped if a UTR has already been saved in CR (second time through)" in new Setup {
      val testGroups = Groups(groupRelief = true, Some(GroupCompanyName("testGroupCompanyname1", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        Some(GroupUTR(Some("1234567890"))))
      mockKeystoreFetchAndGet("registrationID", Some(testRegId))
      CTRegistrationConnectorMocks.retrieveCTRegistration(ctDocFirstTimeThrough)
      when(mockCompanyRegistrationConnector.retrieveEmail(any())(any()))
        .thenReturn(Future.successful(Some(Email("verified@email", "GG", linkSent = true, verified = true, returnLinkEmailSent = true))))
      when(mockGroupService.retrieveGroups(any())(any()))
        .thenReturn(Future.successful(Some(testGroups)))

      showWithAuthorisedUser(controller.show) {
        result =>
          val document = Jsoup.parse(contentAsString(result))

          document.title should include("Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?")
          document.getElementsByTag("h1").first.text() shouldBe "Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?"
          document.getElementById("utr").attr("value") shouldBe "1234567890"
          document.getElementsByTag("legend").text() shouldBe "Do you know testGroupCompanyname1’s Unique Taxpayer Reference (UTR)?"
      }
    }
  }
}