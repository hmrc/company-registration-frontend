/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers.internal

import akka.actor.ActorSystem
import connectors.{CohoApiBadRequestResponse, CohoApiErrorResponse, CohoApiSuccessResponse}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.internal.CheckIncorporationService
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class CheckIncorporationControllerSpec extends UnitSpec with MockitoSugar {
  implicit val system = ActorSystem("test")

  val mockCheckIncorporationService = mock[CheckIncorporationService]

  class Setup {
      val controller = new CheckIncorporationController {
        val checkIncorporationService = mockCheckIncorporationService
      }
  }

  "fetchIncorporation" should {

    val timePoint = Some("123456789")
    val itemsPerPage = 1
    val queryString = "/?timepoint=123456789&items_per_page=1"

    val incorporationDetails = Json.parse(
      """{
        |"items":[
        | {
        |   "company_number":"9999999999",
        |   "transaction_status":"accepted",
        |   "transaction_type":"incorporation",
        |   "company_profile_link":"http://api.companieshouse.gov.uk/company/9999999999",
        |   "transaction_id":"7894578956784",
        |   "incorporated_on":"2016-08-10",
        |   "timepoint":"123456789"
        | }
        |],
        |"links":{
        | "next":"https://foo.com/bar?timepoint=123456789"
        |}
        |}""".stripMargin)

    "return a 200 with incorporation details as json" in new Setup {
      when(mockCheckIncorporationService.fetchIncorporationStatus(Matchers.eq(timePoint), Matchers.eq(itemsPerPage))(Matchers.any()))
        .thenReturn(Future.successful(CohoApiSuccessResponse(incorporationDetails)))

      val result = await(controller.fetchIncorporation(timePoint, itemsPerPage)(FakeRequest()))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe incorporationDetails
    }

    "return a 400 if the coho Api http call returns a bad request" in new Setup {
      when(mockCheckIncorporationService.fetchIncorporationStatus(Matchers.eq(timePoint), Matchers.eq(itemsPerPage))(Matchers.any()))
        .thenReturn(Future.successful(CohoApiBadRequestResponse))

      val result = await(controller.fetchIncorporation(timePoint, itemsPerPage)(FakeRequest()))
      status(result) shouldBe BAD_REQUEST
    }

    "return a 502 if the coho api http call returns an unexpected response" in new Setup {
      when(mockCheckIncorporationService.fetchIncorporationStatus(Matchers.eq(timePoint), Matchers.eq(itemsPerPage))(Matchers.any()))
        .thenReturn(Future.successful(CohoApiErrorResponse(new Exception(""))))

      val result = await(controller.fetchIncorporation(timePoint, itemsPerPage)(FakeRequest()))
      status(result) shouldBe BAD_GATEWAY
    }
  }
}
