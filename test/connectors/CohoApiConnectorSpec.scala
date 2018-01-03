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

package connectors

import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.play.http.ws.{WSHttp, WSProxy}
import uk.gov.hmrc.play.test.UnitSpec
import utils.{BooleanFeatureSwitch, SCRSFeatureSwitches}

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.logging.Authorization

class CohoApiConnectorSpec extends UnitSpec with MockitoSugar {

  val mockHTTP = mock[WSHttp]
  val mockHTTPProxy = mock[WSHttp with WSProxy]
  val mockFeatureSwitch = mock[SCRSFeatureSwitches]

  val testStubUrl = "testCohoAPIStubUrl"
  val testAPIUrl = "testCohoAPIUrl"
  val testToken = "testToken"

  class SetupWithProxy(proxy: Boolean) {
    val connector = new CohoAPIConnector {
      val cohoAPIStubUrl = testStubUrl
      val httpNoProxy = mockHTTP
      val httpProxy = mockHTTPProxy
      val cohoAPIUrl = testAPIUrl
      val cohoApiAuthToken = testToken
      val featureSwitch = mockFeatureSwitch

      override def useProxy = proxy
    }
  }

  "buildQueryString" should {

    val timePoint = "123456789"
    val itemsPerPage = 1

    "return a query string containing timepoint and items_per_page" in new SetupWithProxy(true) {
      connector.buildQueryString(Some(timePoint), itemsPerPage) shouldBe "?timepoint=123456789&items_per_page=1"
    }

    "return a query string containing only an items_per_page" in new SetupWithProxy(true) {
      connector.buildQueryString(None, itemsPerPage) shouldBe "?items_per_page=1"
    }
  }

  "fetchIncorporationStatus" should {

    val timepoint = Some("123456789")
    val itemsPerPage = 1
    val queryString = "?timepoint=123456789&items_per_page=1"
    val url = s"$testAPIUrl/submissions$queryString"

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
        | "next":"https://foo.com?bar=123456789"
        |}
        |}""".stripMargin)

    implicit val hc = HeaderCarrier()

    "append the Coho API token to the HeaderCarrier as an Authorization header when using the proxy" in new SetupWithProxy(true) {
      val captor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      when(mockHTTPProxy.GET[HttpResponse](Matchers.any())(Matchers.any(), captor.capture(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(incorporationDetails))))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage))

      captor.getValue.authorization shouldBe Some(Authorization(s"Bearer $testToken"))

    }

    "return a CohoApiSuccessResponse with a json response containing details of an incorporation when using the proxy" in new SetupWithProxy(true) {
      when(mockHTTPProxy.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(incorporationDetails))))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiSuccessResponse(incorporationDetails)
    }

    "return a CohoApiSuccessResponse with a json response containing details of an incorporation without using the proxy" in new SetupWithProxy(false) {
      when(mockHTTP.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.successful(HttpResponse(200, Some(incorporationDetails))))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiSuccessResponse(incorporationDetails)
    }

    "return a CohoApiBadRequestResponse response when a bad request is returned from the http request when using the proxy" in new SetupWithProxy(true) {
      when(mockHTTPProxy.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("ex")))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiBadRequestResponse
    }

    "return a CohoApiBadRequestResponse response when a bad request is returned from the http request without using the proxy" in new SetupWithProxy(false) {
      when(mockHTTP.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(new BadRequestException("ex")))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiBadRequestResponse
    }

    val ex = new Exception("")

    "return a CohoApiErrorResponse if an unexpected exception is caught when using the proxy" in new SetupWithProxy(true) {
      when(mockHTTPProxy.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(ex))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiErrorResponse(ex)
    }

    "return a CohoApiErrorResponse if an unexpected exception is caught without using the proxy" in new SetupWithProxy(false) {
      when(mockHTTP.GET[HttpResponse](Matchers.any())(Matchers.any(), Matchers.any(), Matchers.any[ExecutionContext]))
        .thenReturn(Future.failed(ex))

      await(connector.fetchIncorporationStatus(timepoint, itemsPerPage)) shouldBe CohoApiErrorResponse(ex)
    }
  }

  class SetupNoProxy {
    val connector = new CohoAPIConnector {
      val cohoAPIStubUrl = testStubUrl
      val httpNoProxy = mockHTTP
      val httpProxy = mockHTTPProxy
      val cohoAPIUrl = testAPIUrl
      val cohoApiAuthToken = testToken
      val featureSwitch = mockFeatureSwitch
    }
  }

  "useProxy" should {

    "return true when the feature is enabled" in new SetupNoProxy {
      val feature = BooleanFeatureSwitch(SCRSFeatureSwitches.COHO, enabled = true)
      when(mockFeatureSwitch.cohoFirstHandOff).thenReturn(feature)
      connector.useProxy shouldBe true
    }

    "return false when the feature is disabled" in new SetupNoProxy {
      val feature = BooleanFeatureSwitch(SCRSFeatureSwitches.COHO, enabled = false)
      when(mockFeatureSwitch.cohoFirstHandOff).thenReturn(feature)
      connector.useProxy shouldBe false
    }
  }

}
