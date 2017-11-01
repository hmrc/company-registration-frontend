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

package connectors

import connectors.GovUkConnector.getConfString
import models.IncorporationResponse
import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSHttp
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.workingdays.{BankHoliday, BankHolidaySet}

import scala.concurrent.Future

class GovUkConnectorSpec extends UnitSpec with WithFakeApplication with MockitoSugar with ServicesConfig {

  val mockHttp: WSHttp = mock[WSHttp]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val bankHoliday = BankHoliday("testHoliday", LocalDate.now())
  val bankHolidaySet = BankHolidaySet("testdivision", List(bankHoliday))

  class Setup {
    object TestGovConnector extends GovUkConnector {
      val http: WSHttp = mockHttp
      override val bankHolidaysUrl: String = getConfString("bank-holidays.url", throw new Exception("bank-holidays.url not found"))
    }
  }

  "retrieve bank holidays" should {
    "return the bank holidays" in new Setup {
      when(mockHttp.GET[Map[String, BankHolidaySet]](Matchers.any())(Matchers.any(), Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(Map(TestGovConnector.englandAndWales -> bankHolidaySet)))
      val response = TestGovConnector.retrieveBankHolidaysForEnglandAndWales
      await(response).getClass shouldBe bankHolidaySet.getClass
    }
  }
}
