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

package mocks

import connectors._
import fixtures.{CompanyDetailsFixture, CorporationTaxFixture}
import models._
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads}

import scala.concurrent.Future

trait CompanyRegistrationConnectorMock extends CorporationTaxFixture {
  this: MockitoSugar =>

  lazy val mockCompanyRegistrationConnector = mock[CompanyRegistrationConnector]

  object CTRegistrationConnectorMocks {
    def retrieveCTData(ctData: Option[CorporationTaxRegistrationResponse]): OngoingStubbing[Future[Option[CorporationTaxRegistrationResponse]]] = {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistrationDetails(Matchers.anyString())
      (Matchers.any[HeaderCarrier](), Matchers.any[HttpReads[CorporationTaxRegistrationResponse]]()))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCTRegistration(ctData: JsValue = buildCorporationTaxModel()) = {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(Matchers.anyString())
      (Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(ctData))
    }

    def createCTDataEntry(reg: CorporationTaxRegistrationResponse): OngoingStubbing[Future[CorporationTaxRegistrationResponse]] = {
      when(mockCompanyRegistrationConnector.createCorporationTaxRegistrationDetails(Matchers.any[String]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(reg))
    }

    def retrieveCompanyDetails(response: Option[CompanyDetails]) = {
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(Matchers.anyString())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }

    def updateCompanyDetails(response : CompanyDetails) = {
      when(mockCompanyRegistrationConnector.updateCompanyDetails(Matchers.anyString(), Matchers.any[CompanyDetails]())(Matchers.any()))
        .thenReturn(Future.successful(response))
    }

    def retrieveContactDetails(response: CompanyContactDetailsResponse) = {
      when(mockCompanyRegistrationConnector.retrieveContactDetails(Matchers.anyString())(Matchers.any()))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(response: CompanyContactDetailsResponse) ={
      when(mockCompanyRegistrationConnector.updateContactDetails(Matchers.anyString(), Matchers.any[CompanyContactDetailsMongo]())(Matchers.any()))
        .thenReturn(Future.successful(response))
    }

    def retrieveTradingDetails(response : Option[TradingDetails]) = {
      when(mockCompanyRegistrationConnector.retrieveTradingDetails(Matchers.anyString())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }

    def updateTradingDetails(response : TradingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.updateTradingDetails(Matchers.anyString(), Matchers.eq(TradingDetails("false")))(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }

    def retrieveAccountingDetails(response: AccountingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.retrieveAccountingDetails(Matchers.anyString())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }

    def updateAccountingDetails(response: AccountingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.updateAccountingDetails(Matchers.anyString(), Matchers.any[AccountingDetailsRequest]())(Matchers.any[HeaderCarrier]()))
        .thenReturn(Future.successful(response))
    }

    def fetchAcknowledgementReference(regID: String, returns: ConfirmationReferencesResponse) = {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(Matchers.contains(regID))(Matchers.any()))
        .thenReturn(Future.successful(returns))
    }
  }
}
