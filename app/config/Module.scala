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

package config

import com.google.inject.AbstractModule
import config.filters.{SessionIdFilter, SessionIdFilterImpl}
import connectors._
import controllers.dashboard.{CancelRegistrationController, CancelRegistrationControllerImpl, DashboardController, DashboardControllerImpl}
import controllers.feedback.{FeedbackController, FeedbackControllerImpl}
import controllers.groups._
import controllers.handoff._
import controllers.healthcheck.{HealthCheckController, HealthCheckControllerImpl}
import controllers.reg._
import controllers.test._
import controllers.verification.{EmailVerificationController, EmailVerificationControllerImpl}
import controllers.{PolicyController, PolicyControllerImpl}
import repositories.{NavModelRepo, NavModelRepoImpl}
import services._
import services.internal.{TestIncorporationService, TestIncorporationServiceImpl}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedCache, ShortLivedHttpCaching}
import utils._

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CryptoInitialiser]).to(classOf[CryptoInitialiserImpl]).asEagerSingleton()
    bind(classOf[FrontendErrorHandlerSCRS]).to(classOf[SCRSErrorHandler]).asEagerSingleton()

    bind(classOf[SessionIdFilter]).to(classOf[SessionIdFilterImpl]).asEagerSingleton()
    bind(classOf[WSHttp]).to(classOf[WSHttpImpl]).asEagerSingleton()
    bind(classOf[JweCommon]).to(classOf[Jwe]).asEagerSingleton()
    bind(classOf[NavModelRepo]).to(classOf[NavModelRepoImpl]).asEagerSingleton()
    // connectors
    bind(classOf[PlayAuthConnector]).to(classOf[FrontendAuthConnector]).asEagerSingleton()
    bind(classOf[AddressLookupConnector]).to(classOf[AddressLookupConnectorImpl]).asEagerSingleton()
    bind(classOf[DeskproConnector]).to(classOf[DeskproConnectorImpl]).asEagerSingleton()
    bind(classOf[IncorpInfoConnector]).to(classOf[IncorpInfoConnectorImpl]).asEagerSingleton()
    bind(classOf[BusinessRegistrationConnector]).to(classOf[BusinessRegistrationConnectorImpl]).asEagerSingleton()
    bind(classOf[CompanyRegistrationConnector]).to(classOf[CompanyRegistrationConnectorImpl]).asEagerSingleton()

    bind(classOf[DynamicStubConnector]).to(classOf[DynamicStubConnectorImpl]).asEagerSingleton()
    bind(classOf[EmailVerificationConnector]).to(classOf[EmailVerificationConnectorImpl]).asEagerSingleton()
    bind(classOf[SessionCache]).to(classOf[SCRSSessionCache]).asEagerSingleton()
    bind(classOf[KeystoreConnector]).to(classOf[KeystoreConnectorImpl]).asEagerSingleton()
    bind(classOf[PlatformAnalyticsConnector]).to(classOf[PlatformAnalyticsConnectorImpl]).asEagerSingleton()
    bind(classOf[S4LConnector]).to(classOf[S4LConnectorImpl]).asEagerSingleton()


    bind(classOf[ShortLivedCache]).to(classOf[SCRSShortLivedCache]).asEagerSingleton()
    bind(classOf[ShortLivedHttpCaching]).to(classOf[SCRSShortLivedHttpCaching]).asEagerSingleton()
    bind(classOf[SendTemplatedEmailConnector]).to(classOf[SendTemplatedEmailConnectorImpl]).asEagerSingleton()
    bind(classOf[PAYEConnector]).to(classOf[PAYEConnectorImpl]).asEagerSingleton()
    bind(classOf[VATConnector]).to(classOf[VATConnectorImpl]).asEagerSingleton()
    bind(classOf[VatThresholdConnector]).to(classOf[VatThresholdConnectorImpl]).asEagerSingleton()

    bind(classOf[SCRSFeatureSwitches]).to(classOf[SCRSFeatureSwitchesImpl]).asEagerSingleton()
    bind(classOf[FeatureSwitchManager]).to(classOf[FeatureSwitchManagerImpl]).asEagerSingleton()


    //services
    bind(classOf[ThresholdService]).to(classOf[ThresholdServiceImpl]).asEagerSingleton()
    bind(classOf[DashboardService]).to(classOf[DashboardServiceImpl]).asEagerSingleton()
    bind(classOf[HandOffService]).to(classOf[HandOffServiceImpl]).asEagerSingleton()
    bind(classOf[HandBackService]).to(classOf[HandBackServiceImpl]).asEagerSingleton()
    bind(classOf[EnrolmentsService]).to(classOf[EnrolmentsServiceImpl]).asEagerSingleton()
    bind(classOf[MetricsService]).to(classOf[MetricsServiceImpl]).asEagerSingleton()
    bind(classOf[AccountingService]).to(classOf[AccountingServiceImpl]).asEagerSingleton()
    bind(classOf[TimeService]).to(classOf[TimeServiceImpl]).asEagerSingleton()
    bind(classOf[CompanyContactDetailsService]).to(classOf[CompanyContactDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[MetaDataService]).to(classOf[MetaDataServiceImpl]).asEagerSingleton()
    bind(classOf[DeskproService]).to(classOf[DeskproServiceImpl]).asEagerSingleton()
    bind(classOf[PPOBService]).to(classOf[PPOBServiceImpl]).asEagerSingleton()
    bind(classOf[QuestionnaireService]).to(classOf[QuestionnaireServiceImpl]).asEagerSingleton()
    bind(classOf[EmailVerificationService]).to(classOf[EmailVerificationServiceImpl]).asEagerSingleton()
    bind(classOf[DeleteSubmissionService]).to(classOf[DeleteSubmissionServiceImpl]).asEagerSingleton()
    bind(classOf[EnrolmentsService]).to(classOf[EnrolmentsServiceImpl]).asEagerSingleton()
    bind(classOf[TradingDetailsService]).to(classOf[TradingDetailsServiceImpl]).asEagerSingleton()
    bind(classOf[TestIncorporationService]).to(classOf[TestIncorporationServiceImpl]).asEagerSingleton()

    //controllers
    bind(classOf[CancelRegistrationController]).to(classOf[CancelRegistrationControllerImpl]).asEagerSingleton()
    bind(classOf[FeedbackController]).to(classOf[FeedbackControllerImpl]).asEagerSingleton()
    bind(classOf[BasicCompanyDetailsController]).to(classOf[BasicCompanyDetailsControllerImpl])
    bind(classOf[BusinessActivitiesController]).to(classOf[BusinessActivitiesControllerImpl]).asEagerSingleton()
    bind(classOf[CorporationTaxDetailsController]).to(classOf[CorporationTaxDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[IncorporationSummaryController]).to(classOf[IncorporationSummaryControllerImpl]).asEagerSingleton()
    bind(classOf[RegistrationConfirmationController]).to(classOf[RegistrationConfirmationControllerImpl]).asEagerSingleton()
    bind(classOf[CorporationTaxSummaryController]).to(classOf[CorporationTaxSummaryControllerImpl]).asEagerSingleton()

    bind(classOf[AccountingDatesController]).to(classOf[AccountingDatesControllerImpl]).asEagerSingleton()

    bind(classOf[HealthCheckController]).to(classOf[HealthCheckControllerImpl]).asEagerSingleton()
    bind(classOf[ApplicationInProgressController]).to(classOf[ApplicationInProgressControllerImpl]).asEagerSingleton()
    bind(classOf[CompanyContactDetailsController]).to(classOf[CompanyContactDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[DashboardController]).to(classOf[DashboardControllerImpl]).asEagerSingleton()
    bind(classOf[CompletionCapacityController]).to(classOf[CompletionCapacityControllerImpl]).asEagerSingleton()
    bind(classOf[ConfirmationController]).to(classOf[ConfirmationControllerImpl]).asEagerSingleton()
    bind(classOf[IncompleteRegistrationController]).to(classOf[IncompleteRegistrationControllerImpl]).asEagerSingleton()
    bind(classOf[IndexController]).to(classOf[IndexControllerImpl]).asEagerSingleton()
    bind(classOf[LimitReachedController]).to(classOf[LimitReachedControllerImpl]).asEagerSingleton()
    bind(classOf[PPOBController]).to(classOf[PPOBControllerImpl]).asEagerSingleton()
    bind(classOf[QuestionnaireController]).to(classOf[QuestionnaireControllerImpl]).asEagerSingleton()
    bind(classOf[RegistrationEmailConfirmationController]).to(classOf[RegistrationEmailConfirmationControllerImpl]).asEagerSingleton()
    bind(classOf[RegistrationEmailController]).to(classOf[RegistrationEmailControllerImpl]).asEagerSingleton()
    bind(classOf[RegistrationUnsuccessfulController]).to(classOf[RegistrationUnsuccessfulControllerImpl]).asEagerSingleton()
    bind(classOf[ReturningUserController]).to(classOf[ReturningUserControllerImpl]).asEagerSingleton()
    bind(classOf[SignInOutController]).to(classOf[SignInOutControllerImpl]).asEagerSingleton()
    bind(classOf[SummaryController]).to(classOf[SummaryControllerImpl]).asEagerSingleton()
    bind(classOf[TradingDetailsController]).to(classOf[TradingDetailsControllerImpl]).asEagerSingleton()
    bind(classOf[WelcomeController]).to(classOf[WelcomeControllerImpl]).asEagerSingleton()
    bind(classOf[EmailVerificationController]).to(classOf[EmailVerificationControllerImpl]).asEagerSingleton()

    //test controllers
    bind(classOf[CTMongoTestController]).to(classOf[CTMongoTestControllerImpl]).asEagerSingleton()
    bind(classOf[EditSessionController]).to(classOf[EditSessionControllerImpl]).asEagerSingleton()
    bind(classOf[ETMPNotificationTestController]).to(classOf[ETMPNotificationTestControllerImpl]).asEagerSingleton()
    bind(classOf[FeatureSwitchController]).to(classOf[FeatureSwitchControllerImpl]).asEagerSingleton()
    bind(classOf[ModifyThrottledUsersController]).to(classOf[ModifyThrottledUsersControllerImpl]).asEagerSingleton()
    bind(classOf[SubmissionEndpointController]).to(classOf[SubmissionEndpointControllerImpl]).asEagerSingleton()
    bind(classOf[TestEndpointController]).to(classOf[TestEndpointControllerImpl]).asEagerSingleton()
    bind(classOf[TestIncorporateController]).to(classOf[TestIncorporateControllerImpl]).asEagerSingleton()
    bind(classOf[PolicyController]).to(classOf[PolicyControllerImpl]).asEagerSingleton()
    }
  }