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

package mocks

import connectors._
import fixtures.CorporationTaxFixture
import models._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import scala.concurrent.{ExecutionContext, Future}

trait CompanyRegistrationConnectorMock extends CorporationTaxFixture {
  this: MockitoSugar =>

  lazy val mockCompanyRegistrationConnector = mock[CompanyRegistrationConnector]

  object CTRegistrationConnectorMocks {
    def retrieveCTData(ctData: Option[CorporationTaxRegistrationResponse]): OngoingStubbing[Future[Option[CorporationTaxRegistrationResponse]]] = {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistrationDetails(ArgumentMatchers.anyString())
      (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext](), ArgumentMatchers.any[HttpReads[CorporationTaxRegistrationResponse]]()))
        .thenReturn(Future.successful(ctData))
    }

    def retrieveCTRegistration(ctData: JsValue = buildCorporationTaxModel()) = {
      when(mockCompanyRegistrationConnector.retrieveCorporationTaxRegistration(ArgumentMatchers.anyString())
      (ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(ctData))
    }

    def createCTDataEntry(reg: CorporationTaxRegistrationResponse): OngoingStubbing[Future[CorporationTaxRegistrationResponse]] = {
      when(mockCompanyRegistrationConnector.createCorporationTaxRegistrationDetails(ArgumentMatchers.any[String]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(reg))
    }

    def retrieveCompanyDetails(response: Option[CompanyDetails]) = {
      when(mockCompanyRegistrationConnector.retrieveCompanyDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def updateCompanyDetails(response : CompanyDetails) = {
      when(mockCompanyRegistrationConnector.updateCompanyDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[CompanyDetails]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def retrieveContactDetails(response: CompanyContactDetailsResponse) = {
      when(mockCompanyRegistrationConnector.retrieveContactDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def updateContactDetails(response: CompanyContactDetailsResponse) ={
      when(mockCompanyRegistrationConnector.updateContactDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[CompanyContactDetailsApi]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def retrieveTradingDetails(response : Option[TradingDetails]) = {
      when(mockCompanyRegistrationConnector.retrieveTradingDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def updateTradingDetails(response : TradingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.updateTradingDetails(ArgumentMatchers.anyString(), ArgumentMatchers.eq(TradingDetails("false")))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def retrieveAccountingDetails(response: AccountingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.retrieveAccountingDetails(ArgumentMatchers.anyString())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def updateAccountingDetails(response: AccountingDetailsResponse) = {
      when(mockCompanyRegistrationConnector.updateAccountingDetails(ArgumentMatchers.anyString(), ArgumentMatchers.any[AccountingDetailsRequest]())(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(response))
    }

    def fetchAcknowledgementReference(regID: String, returns: ConfirmationReferencesResponse) = {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(ArgumentMatchers.contains(regID))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(Future.successful(returns))
    }

    def validateRegisteredOfficeAddress(registrationID: String, ro: CHROAddress)(response: Future[Option[NewAddress]]): Unit = {
      when(mockCompanyRegistrationConnector.validateRegisteredOfficeAddress(ArgumentMatchers.eq(registrationID), ArgumentMatchers.eq(ro))(ArgumentMatchers.any[HeaderCarrier](), ArgumentMatchers.any[ExecutionContext]()))
        .thenReturn(response)
    }
  }
}
