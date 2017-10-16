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

import connectors.{KeystoreConnector, IncorpInfoConnector}
import org.mockito.Matchers
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo, any}
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.JsValue
import services._
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait SCRSMocks
  extends CompanyContactDetailsServiceMock
    with AddressLookupServiceMock
    with AccountingServiceMock
    with CompanyRegistrationConnectorMock
    with KeystoreMock
    with SaveForLaterMock
    with WSHTTPMock
    with HandOffServiceMock
    with HandBackServiceMock
    with NavModelRepoMock {
  this: MockitoSugar =>

  lazy val mockAuthConnector = mock[AuthConnector]
  lazy val mockSessionCache = mock[SessionCache]
  lazy val mockAudit = mock[Audit]
  lazy val mockAuditConnector = mock[AuditConnector]
  lazy val mockIncorpInfoConnector = mock[IncorpInfoConnector]
  lazy val mockDeleteSubmissionService = mock[DeleteSubmissionService]
  lazy val mockPPOBService = mock[PPOBService]


  def mockFetchRegistrationID[T <: CommonService](response: String, mock : T) = {
    when(mock.fetchRegistrationID(Matchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(response))
  }

  def mockCacheRegistrationID(registrationId: String, mock: KeystoreConnector = mockKeystoreConnector) = {
    when(mock.cache[String](any(), any())(any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty[String, JsValue])))
  }

  def resetMocks() = {
    reset(mockAuthConnector)
    reset(mockAuditConnector)
    reset(mockS4LConnector)
    reset(mockWSHttp)
    reset(mockAddressLookupService)
    reset(mockKeystoreConnector)
    reset(mockSessionCache)
    reset(mockCompanyRegistrationConnector)
    reset(mockHandBackService)
    reset(mockPPOBService)
    reset(mockHandOffService)
    reset(mockCompanyContactDetailsService)
    reset(mockAudit)
    reset(mockNavModelRepo)
  }
}
