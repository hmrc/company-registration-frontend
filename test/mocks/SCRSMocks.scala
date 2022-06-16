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

import config.AppConfig
import connectors._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.JsValue
import services._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.Audit
import utils.{BooleanFeatureSwitch, FeatureSwitchManager, JweCommon, SCRSFeatureSwitches}

import scala.concurrent.Future

trait SCRSMocks extends CompanyContactDetailsServiceMock
  with AccountingServiceMock
  with CompanyRegistrationConnectorMock
  with KeystoreMock
  with SaveForLaterMock
  with WSHTTPMock
  with HandOffServiceMock
  with HandBackServiceMock
  with NavModelRepoMock
  with PrepopAddressConnectorMock
  with AddressLookupConnectorMock {
  this: MockitoSugar =>

  val mockMetricsService = mock[MetricsService]

  lazy val mockSessionCache = mock[SessionCache]
  lazy val mockAudit = mock[Audit]
  lazy val mockAuditConnector = mock[AuditConnector]
  lazy val mockIncorpInfoConnector = mock[IncorpInfoConnector]
  lazy val mockDeleteSubmissionService = mock[DeleteSubmissionService]
  lazy val mockEmailService = mock[EmailVerificationService]
  lazy val mockPPOBService = mock[PPOBService]
  lazy val mockCommonService = mock[CommonService]
  lazy val mockMetaDataService = mock[MetaDataService]
  lazy val mockThresholdService = mock[ThresholdService]
  lazy val mockAddressLookupService = mock[AddressLookupFrontendService]
  lazy val mockEmailVerificationConnector = mock[EmailVerificationConnector]
  lazy val mockSendTemplateEmailConnector = mock[SendTemplatedEmailConnector]
  lazy implicit val mockAppConfig = mock[AppConfig]
  lazy val mockPAYEConnector = mock[PAYEConnector]
  lazy val mockVATConnector = mock[VATConnector]
  lazy val mockSCRSFeatureSwitches = mock[SCRSFeatureSwitches]
  lazy val mockFeatureSwitchManager = mock[FeatureSwitchManager]
  lazy val mockJweCommon = mock[JweCommon]
  lazy val mockConfiguration = mock[Configuration]
  lazy val mockTimeService = mock[TimeService]
  lazy val mockGroupService = mock[GroupService]


  def mockFetchRegistrationID[T <: CommonService](response: String, mock: T) = {
    when(mock.fetchRegistrationID(ArgumentMatchers.any[HeaderCarrier]()))
      .thenReturn(Future.successful(response))
  }

  def mockCacheRegistrationID(registrationId: String, mock: KeystoreConnector = mockKeystoreConnector) = {
    when(mock.cache[String](any(), any())(any(), any())).thenReturn(Future.successful(CacheMap("", Map.empty[String, JsValue])))
  }

  def resetMocks(): Unit = {
    reset(mockAuditConnector)
    reset(mockS4LConnector)
    reset(mockWSHttp)
    reset(mockKeystoreConnector)
    reset(mockSessionCache)
    reset(mockCompanyRegistrationConnector)
    reset(mockHandBackService)
    reset(mockPPOBService)
    reset(mockHandOffService)
    reset(mockCompanyContactDetailsService)
    reset(mockAudit)
    reset(mockNavModelRepo)
    reset(mockEmailService)
    reset(mockCommonService)
    reset(mockEmailVerificationConnector)
    reset(mockSendTemplateEmailConnector)
    reset(mockPAYEConnector)
    reset(mockVATConnector)
    reset(mockSCRSFeatureSwitches)
    reset(mockJweCommon)
    reset(mockThresholdService)
    reset(mockFeatureSwitchManager)
    reset(mockAddressLookupConnector)
    reset(mockAddressLookupService)
    reset(mockConfiguration)
    reset(mockTimeService)
    reset(mockMetricsService)
    reset(mockGroupService)
  }
}
