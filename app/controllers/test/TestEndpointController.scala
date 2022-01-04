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

package controllers.test

import config.FrontendAppConfig
import connectors._
import controllers.auth.AuthenticatedController
import forms._
import forms.test.{CompanyContactTestEndpointForm, FeatureSwitchForm}

import javax.inject.{Inject, Singleton}
import models._
import models.connectors.ConfirmationReferences
import models.handoff._
import models.test.FeatureSwitch
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.NavModelRepo
import services._
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.auth.core.retrieve.Retrievals
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import utils._
import views.html.dashboard.{Dashboard => DashboardView}
import views.html.reg.{TestEndpoint => TestEndpointView}
import views.html.test.{TestEndpointSummary => TestEndpointSummaryView}
import views.html.test.{FeatureSwitch => FeatureSwitchView}
import views.html.test.{PrePopAddresses => PrePopAddressesView}
import views.html.test.{PrePopContactDetails => PrePopContactDetailsView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TestEndpointController @Inject()(
                                        val authConnector: PlayAuthConnector,
                                        val s4LConnector: S4LConnector,
                                        val keystoreConnector: KeystoreConnector,
                                        val compRegConnector: CompanyRegistrationConnector,
                                        val scrsFeatureSwitches: SCRSFeatureSwitches,
                                        val metaDataService: MetaDataService,
                                        val dynStubConnector: DynamicStubConnector,
                                        val brConnector: BusinessRegistrationConnector,
                                        val navModelRepo: NavModelRepo,
                                        val dashboardService: DashboardService,
                                        val timeService: TimeService,
                                        val handOffService: HandOffService,
                                        val featureSwitchManager: FeatureSwitchManager,
                                        val controllerComponents: MessagesControllerComponents,
                                        viewTestEndpoint: TestEndpointView,
                                        viewTestEndpointSummary: TestEndpointSummaryView,
                                        viewFeatureSwitch: FeatureSwitchView,
                                        viewPrePopAddresses: PrePopAddressesView,
                                        viewPrePopContactDetails: PrePopContactDetailsView,
                                        viewDashboard: DashboardView
                                      )(implicit val appConfig: FrontendAppConfig, implicit val ec: ExecutionContext)
  extends AuthenticatedController with CommonService
    with SCRSExceptions with SessionRegistration with I18nSupport {
  lazy val navModelMongo = navModelRepo.repository

  lazy val coHoURL = appConfig.servicesConfig.getConfString("coho-service.sign-in", throw new Exception("Could not find config for coho-sign-in url"))

  lazy val accDForm: AccountingDatesFormT = new AccountingDatesForm(timeService)


  private def convertToForm(data: CompanyNameHandOffIncoming): CompanyNameHandOffFormModel = {
    CompanyNameHandOffFormModel(
      registration_id = data.journey_id,
      openidconnectid = data.user_id,
      company_name = data.company_name,
      registered_office_address = data.registered_office_address,
      jurisdiction = data.jurisdiction,
      ch = data.ch.value.get("foo").toString,
      hmrc = data.hmrc.value.get("foo").toString
    )
  }

  val getAllS4LEntries: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.internalId) { internalID =>
        for {
          regID <- keystoreConnector.fetchAndGet[String]("registrationID") map {
            case Some(res) => res
            case None => cacheRegistrationID("1"); "1"
          }
          applicantDetails <- metaDataService.getApplicantData(regID) recover { case _ => AboutYouChoice("Director") }
          companyDetails <- compRegConnector.retrieveCompanyDetails(regID)
          accountingDates <- compRegConnector.retrieveAccountingDetails(regID)
          contactDetails <- compRegConnector.retrieveContactDetails(regID)
          tradingDetails <- compRegConnector.retrieveTradingDetails(regID)
          handBackData <- s4LConnector.fetchAndGet[CompanyNameHandOffIncoming](internalID, "HandBackData")
          cTRecord <- compRegConnector.retrieveCorporationTaxRegistration(regID)
        } yield {
          val applicantForm = AboutYouForm.endpointForm.fill(applicantDetails)
          val companyDetailsForm = CompanyDetailsForm.form.fill(
            companyDetails.getOrElse(
              CompanyDetails("testCompanyName",
                CHROAddress("testPremises", "testAddressLine1", None, "testLocality", "UK", None, Some("ZZ1 1ZZ"), None), PPOB("RO", None), jurisdiction = "testJurisdiction")
            )
          )
          val accountingDatesForm = accDForm.form.fill(accountingDates match {
            case AccountingDetailsSuccessResponse(success) => success
            case _ => AccountingDatesModel("WHEN_REGISTERED", None, None, None)
          })
          val handBackForm = FirstHandBackForm.form.fill(handBackData match {
            case Some(data) => convertToForm(data)
            case _ => CompanyNameHandOffFormModel(None, "", "", CHROAddress("", "", Some(""), "", "", Some(""), Some(""), Some("")), "", "", "")
          })
          val companyContactForm = CompanyContactTestEndpointForm.form.fill(contactDetails match {
            case CompanyContactDetailsSuccessResponse(x) => CompanyContactDetails.toApiModel(x)
            case _ => CompanyContactDetailsApi(None, None, None)
          })
          val tradingDetailsForm = TradingDetailsForm.form.fill(tradingDetails.getOrElse(TradingDetails()))
          Ok(viewTestEndpoint(accountingDatesForm, handBackForm, companyContactForm, companyDetailsForm, tradingDetailsForm, applicantForm))
        }
      }
  }

  val postAllS4LEntries: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        val applicantData = AboutYouForm.endpointForm.bindFromRequest().get
        lazy val accountingDates = accDForm.form.bindFromRequest().get
        val companyContactDetails = CompanyContactTestEndpointForm.form.bindFromRequest().get
        val companyDetailsRequest = CompanyDetailsForm.form.bindFromRequest().get
        val tradingDetailsRequest = TradingDetailsForm.form.bindFromRequest().get
        for {
          regID <- fetchRegistrationID
          _ <- metaDataService.updateApplicantDataEndpoint(applicantData)
          _ <- compRegConnector.updateCompanyDetails(regID, companyDetailsRequest)
          _ <- compRegConnector.updateAccountingDetails(regID, AccountingDetailsRequest.toRequest(accountingDates))
          _ <- compRegConnector.updateContactDetails(regID, companyContactDetails)
          _ <- compRegConnector.updateTradingDetails(regID, tradingDetailsRequest)
        } yield Redirect(routes.TestEndpointController.getAllS4LEntries())
      }
  }

  val clearAllS4LEntries: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.internalId) { internalID =>
        s4LConnector.clear(internalID) map {
          _ => Ok(s"S4L for user oid ${internalID} cleared")
        }
      }
  }

  val clearKeystore: Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorisedOptStr(Retrievals.internalId) { internalID =>
        keystoreConnector.remove() map {
          _ => Ok(s"Keystore for user $internalID cleared")
        }
      }
  }

  def showFeatureSwitch = Action.async {
    implicit request =>
      val firstHandOffSwitch = fetchFirstHandOffSwitch.toString
      val legacyEnvSwitch = fetchLegacyEnvSwitch.toString
      val takeoverSwitch = fetchTakeoverSwitch.toString
      val form = FeatureSwitchForm.form.fill(FeatureSwitch(firstHandOffSwitch, legacyEnvSwitch, takeoverSwitch))

      Future.successful(Ok(viewFeatureSwitch(form)))
  }

  def updateFeatureSwitch() = Action.async {
    implicit request =>
      FeatureSwitchForm.form.bindFromRequest().fold(
        errors => Future.successful(BadRequest(viewFeatureSwitch(errors))),
        success => {
          Seq(
            BooleanFeatureSwitch(scrsFeatureSwitches.COHO, success.firstHandOff.toBoolean),
            BooleanFeatureSwitch(scrsFeatureSwitches.LEGACY_ENV, success.legacyEnv.toBoolean),
            BooleanFeatureSwitch(scrsFeatureSwitches.takeoversKey, success.takeovers.toBoolean)
          ) foreach { fs =>
            fs.enabled match {
              case true => featureSwitchManager.enable(fs)
              case false => featureSwitchManager.disable(fs)
            }
          }

          val form = FeatureSwitchForm.form.fill(success)
          Future.successful(Ok(viewFeatureSwitch(form)))
        }
      )
  }

  private[test] def fetchFirstHandOffSwitch: Boolean = {
    scrsFeatureSwitches(scrsFeatureSwitches.COHO) match {
      case Some(fs) => fs.enabled
      case _ => false
    }
  }

  private[test] def fetchLegacyEnvSwitch: Boolean = {

    scrsFeatureSwitches(scrsFeatureSwitches.LEGACY_ENV) match {
      case Some(fs) => fs.enabled
      case _ => false
    }
  }

  private[test] def fetchTakeoverSwitch: Boolean = {
    scrsFeatureSwitches(scrsFeatureSwitches.takeoversKey) match {
      case Some(fs) => fs.enabled
      case _ => false
    }
  }

  val setupTestNavModel = Action.async {
    implicit request =>
      val nav = HandOffNavModel(
        Sender(
          nav = Map(
            "1" -> NavLinks("http://localhost:9970/register-your-company/corporation-tax-details", "http://localhost:9970/register-your-company/return-to-about-you"),
            "3" -> NavLinks("http://localhost:9970/register-your-company/corporation-tax-summary", "http://localhost:9970/register-your-company/loan-payments-dividends"),
            "5" -> NavLinks("http://localhost:9970/register-your-company/registration-confirmation", "http://localhost:9970/register-your-company/corporation-tax-summary")
          )
        ),
        Receiver(
          nav = Map(
            "0" -> NavLinks("http://localhost:9986/incorporation-frontend-stubs/basic-company-details", ""),
            "2" -> NavLinks("http://localhost:9986/incorporation-frontend-stubs/business-activities", "http://localhost:9986/incorporation-frontend-stubs/company-name-back"),
            "4" -> NavLinks("http://localhost:9986/incorporation-frontend-stubs/incorporation-summary", "http://localhost:9986/incorporation-frontend-stubs/business-activities")
          ),
          jump = Map(
            "company_name" -> "http://localhost:9986/incorporation-frontend-stubs/company-name-back",
            "company_address" -> "http://localhost:9986/incorporation-frontend-stubs/company-name-back",
            "company_jurisdiction" -> "http://localhost:9986/incorporation-frontend-stubs/company-name-back"
          )
        )
      )
      handOffService.cacheNavModel(nav, hc) map (_ => Ok("NavModel created"))
  }

  def simulateDesPost(ackRef: String) = Action.async {
    implicit request =>
      dynStubConnector.simulateDesPost(ackRef).map(_ => Ok)
  }

  def verifyEmail(verified: Boolean) = Action.async {
    implicit request =>
      ctAuthorised {
        def getMetadata(implicit hc: HeaderCarrier, rds: HttpReads[BusinessRegistration]): Future[BusinessRegistration] = {
          brConnector.retrieveMetadata map {
            case BusinessRegistrationSuccessResponse(metaData) => metaData
            case unknown => {
              Logger.warn(s"[TestEndpointController][verifyEmail/getMetadata] HO6 Unexpected result, sending to post-sign-in : ${unknown}")
              throw new RuntimeException(s"Unexpected result ${unknown}")
            }
          }
        }

        for {
          br <- getMetadata
          regId = br.registrationID
          optEmail <- compRegConnector.retrieveEmail(regId)
          emailResponse <- optEmail match {
            case Some(e) => compRegConnector.verifyEmail(regId, e.copy(verified = verified))
            case None => Future.successful(Json.parse("""{"message":"could not find an email for the current logged in user"}"""))
          }
        } yield {
          Ok(emailResponse)
        }
      }
  }

  val testEndpointSummary = Action.async {
    implicit request =>
      Future.successful(Ok(viewTestEndpointSummary()))
  }


  val fetchPrePopAddresses = Action.async {
    implicit request =>
      ctAuthorised {
        registered { regId =>
          brConnector.fetchPrePopAddress(regId) map { js =>
            val addresses = Json.fromJson(js)(Address.prePopReads).get
            Ok(viewPrePopAddresses(addresses))
          }
        }
      }
  }

  val fetchPrePopCompanyContactDetails = Action.async {
    implicit request =>
      ctAuthorised {
        registered { regId =>
          brConnector.fetchPrePopContactDetails(regId) map { js =>
            val contactDetails = Json.fromJson(js)(CompanyContactDetailsApi.prePopReads).get
            Ok(viewPrePopContactDetails(contactDetails))
          }
        }
      }
  }

  private[controllers] def links(cancelUrl: Boolean, restartUrl: Boolean) = {
    ServiceLinks(
      "regURL",
      "otrsURL",
      if (restartUrl) Some("restartUrl") else None,
      if (cancelUrl) Some("cancelUrl") else None)
  }

  def dashboardStubbed(payeStatus: String = "draft",
                       incorpCTStatus: String = "held",
                       payeCancelUrl: String = "true",
                       payeRestartUrl: String = "true",
                       vatStatus: String = "draft",
                       vatCancelUrl: String = "true",
                       ackRefStatus: String = "ackrefStatuses",
                       ctutr: Option[String] = None) = Action {
    implicit request =>
      val incorpAndCTDash = IncorpAndCTDashboard(
        incorpCTStatus,
        Some("submissionDate"),
        Some("transactionID"),
        Some("payementRef"),
        Some("crn"),
        Some("submissionDate"),
        Some("ackRef"),
        Some(ackRefStatus),
        Some("CTUTR")
      )
      payeCancelUrl.toBoolean
      val payeLinks = links(payeCancelUrl.toBoolean, payeRestartUrl.toBoolean)
      val payeDash = ServiceDashboard(payeStatus, Some("lastUpdateDate"), Some("ackrefPaye"), payeLinks, Some(dashboardService.getCurrentPayeThresholds))
      val vatLinks = links(vatCancelUrl.toBoolean, false)
      val vatDash = ServiceDashboard(vatStatus, Some("lastUpdateDate"), Some("ack"), vatLinks, Some(Map("yearly" -> 85000)))

      val dash = Dashboard("companyNameStubbed", incorpAndCTDash, payeDash, vatDash, hasVATCred = true)
      Ok(viewDashboard(dash, coHoURL))
  }

  def handOff6(transactionId: Option[String]): Action[AnyContent] = Action.async {
    implicit request =>
      ctAuthorised {
        registered { regId =>
          val confRefs = ConfirmationReferences(generateTxId(transactionId, regId), Some("PAY_REF-123456789"), Some("12"), "")
          compRegConnector.updateReferences(regId, confRefs) map {
            case ConfirmationReferencesSuccessResponse(refs) => Ok(Json.toJson(refs)(ConfirmationReferences.format))
            case _ => BadRequest
          }
        }
      }
  }

  def generateTxId(transactionId: Option[String], rID: String): String = {
    transactionId match {
      case Some(txid) => txid
      case _ => s"TRANS-ID-${rID}"
    }
  }
}