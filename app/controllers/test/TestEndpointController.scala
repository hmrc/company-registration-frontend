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

package controllers.test

import config.FrontendAuthConnector
import controllers.auth.SCRSRegime
import forms._
import forms.test.{CompanyContactTestEndpointForm, FeatureSwitchForm}
import connectors._
import models._
import models.connectors.ConfirmationReferences
import models.test.FeatureSwitch
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import models.handoff._
import play.api.Logger
import services.{CommonService, HandOffNavigator, MetaDataService}
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.Actions
import uk.gov.hmrc.play.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpReads}
import utils.{BooleanFeatureSwitch, SCRSExceptions, SCRSFeatureSwitches, SessionRegistration, FeatureSwitch => FeatureSwitchUtil}
import views.html.reg.TestEndpoint
import repositories.NavModelRepo
import utils.MessagesSupport

import scala.concurrent.Future


object TestEndpointController extends TestEndpointController {
  val authConnector = FrontendAuthConnector
  val s4LConnector = S4LConnector
  val keystoreConnector = KeystoreConnector
  val compRegConnector = CompanyRegistrationConnector
  val scrsFeatureSwitches = SCRSFeatureSwitches
  val metaDataService = MetaDataService
  val dynStubConnector = DynamicStubConnector
  val brConnector = BusinessRegistrationConnector
  val navModelMongo =  NavModelRepo.repository
  val companyRegistrationConnector = CompanyRegistrationConnector
}

trait TestEndpointController extends FrontendController with Actions with CommonService
  with SCRSExceptions with ServicesConfig with HandOffNavigator with SessionRegistration with MessagesSupport {

  val s4LConnector: S4LConnector
  val keystoreConnector: KeystoreConnector
  val compRegConnector: CompanyRegistrationConnector
  val scrsFeatureSwitches: SCRSFeatureSwitches
  val metaDataService : MetaDataService
  val dynStubConnector: DynamicStubConnector
  val brConnector: BusinessRegistrationConnector
  val coHoURL = getConfString("coho-service.sign-in", throw new Exception("Could not find config for coho-sign-in url"))

  private def convertToForm(data: CompanyNameHandOffIncoming) : CompanyNameHandOffFormModel = {
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

  val getAllS4LEntries = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        for{
          regID <- keystoreConnector.fetchAndGet[String]("registrationID") map {
            case Some(res) => res
            case None => cacheRegistrationID("1"); "1"
          }
          userIds <- authConnector.getIds[UserIDs](user)
          applicantDetails <- metaDataService.getApplicantData
          companyDetails <- compRegConnector.retrieveCompanyDetails(regID)
          accountingDates <- compRegConnector.retrieveAccountingDetails(regID)
          contactDetails <- compRegConnector.retrieveContactDetails(regID)
          tradingDetails <- compRegConnector.retrieveTradingDetails(regID)
          handBackData <- s4LConnector.fetchAndGet[CompanyNameHandOffIncoming](userIds.internalId, "HandBackData")
          cTRecord <- compRegConnector.retrieveCorporationTaxRegistration(regID)
        } yield {
          val applicantForm = AboutYouForm.endpointForm.fill(if(applicantDetails.completionCapacity == "") {
            AboutYouChoice("Director")
          } else {
            applicantDetails
          })
          val companyDetailsForm = CompanyDetailsForm.form.fill(
            companyDetails.getOrElse(
              CompanyDetails("testCompanyName",
                CHROAddress("testPremises", "testAddressLine1", None, "testLocality", "UK", None, Some("ZZ1 1ZZ"), None), PPOB("RO", None), jurisdiction = "testJurisdiction")
            )
          )
          val accountingDatesForm = AccountingDatesForm.form.fill(accountingDates match {
            case AccountingDetailsSuccessResponse(success) => success
            case _ => AccountingDatesModel("WHEN_REGISTERED", None, None, None)
          })
          val handBackForm = FirstHandBackForm.form.fill(handBackData match {
            case Some(data) => convertToForm(data)
            case _ => CompanyNameHandOffFormModel(None, "", "", CHROAddress("","",Some(""),"","",Some(""),Some(""),Some("")), "", "", "")
          })
          val companyContactForm = CompanyContactTestEndpointForm.form.fill(contactDetails match {
            case CompanyContactDetailsSuccessResponse(x) => x
            case _ => CompanyContactDetails.empty
          })
          val tradingDetailsForm = TradingDetailsForm.form.fill(tradingDetails.getOrElse(TradingDetails()))
          Ok(TestEndpoint(accountingDatesForm, handBackForm, companyContactForm, companyDetailsForm, tradingDetailsForm, applicantForm))
        }
  }

  val postAllS4LEntries = AuthorisedFor(taxRegime = SCRSRegime("test-only/post-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        val applicantData = AboutYouForm.endpointForm.bindFromRequest().get
        val accountingDates = AccountingDatesForm.form.bindFromRequest().get
        val companyContactDetails = CompanyContactTestEndpointForm.form.bindFromRequest().get
        val companyDetailsRequest = CompanyDetailsForm.form.bindFromRequest().get
        val tradingDetailsRequest = TradingDetailsForm.form.bindFromRequest().get
        for {
          regID <- fetchRegistrationID
          userIds <- authConnector.getIds[UserIDs](user)
          _ <- metaDataService.updateApplicantDataEndpoint(applicantData)
          _ <- compRegConnector.updateCompanyDetails(regID, companyDetailsRequest)
          _ <- compRegConnector.updateAccountingDetails(regID, AccountingDetailsRequest.toRequest(accountingDates))
          _ <- compRegConnector.updateContactDetails(regID, companyContactDetails)
          _ <- compRegConnector.updateTradingDetails(regID, tradingDetailsRequest)
        } yield Redirect(routes.TestEndpointController.getAllS4LEntries())
  }

  val clearAllS4LEntries = AuthorisedFor(taxRegime = SCRSRegime("test-only/clear-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        authConnector.getIds[UserIDs](user) flatMap {
          userIds =>
            s4LConnector.clear(userIds.internalId) map {
              _ => Ok(s"S4L for user oid ${userIds.internalId} cleared")
            }
        }
  }

  val clearKeystore = AuthorisedFor(taxRegime = SCRSRegime("test-only/clear-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        authConnector.getIds[UserIDs](user) flatMap {
          userIds =>
            keystoreConnector.remove() map {
              _ => Ok(s"Keystore for user ${userIds.internalId} cleared")
            }
        }
  }

  def showFeatureSwitch = Action.async {
    implicit request =>
      val firstHandOffSwitch = fetchFirstHandOffSwitch.toString
      val legacyEnvSwitch = fetchLegacyEnvSwitch.toString
      val form = FeatureSwitchForm.form.fill(FeatureSwitch(firstHandOffSwitch, legacyEnvSwitch))

      Future.successful(Ok(views.html.test.FeatureSwitch(form)))
  }

  def updateFeatureSwitch() = Action.async {
    implicit request =>
      FeatureSwitchForm.form.bindFromRequest().fold(
        errors => Future.successful(BadRequest(views.html.test.FeatureSwitch(errors))),
        success => {
          Seq(
            BooleanFeatureSwitch(SCRSFeatureSwitches.COHO, success.firstHandOff.toBoolean),
            BooleanFeatureSwitch(SCRSFeatureSwitches.LEGACY_ENV, success.legacyEnv.toBoolean)
          ) foreach { fs =>
            fs.enabled match {
              case true => FeatureSwitchUtil.enable(fs)
              case false => FeatureSwitchUtil.disable(fs)
            }
          }

          val form = FeatureSwitchForm.form.fill(success)
          Future.successful(Ok(views.html.test.FeatureSwitch(form)))
        }
      )
  }

  private[test] def fetchFirstHandOffSwitch: Boolean = {
    import SCRSFeatureSwitches.COHO
    scrsFeatureSwitches(COHO) match {
      case Some(fs) => fs.enabled
      case _ => false
    }
  }

  private[test] def fetchLegacyEnvSwitch: Boolean = {
    import SCRSFeatureSwitches.LEGACY_ENV
    scrsFeatureSwitches(LEGACY_ENV) match {
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
              "3" -> NavLinks("http://localhost:9970/register-your-company/corporation-tax-summary", "http://localhost:9970/register-your-company/trading-details"),
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
          cacheNavModel(nav, hc) map (_ => Ok("NavModel created"))
      }

  def checkSubmissionStatus: Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>

        def formatJsonToString(js: JsValue): String = {
          Json.stringify(js)
            .replaceAll("\\{", "\\{</br>")
            .replaceAll(",", ",</br>")
            .replaceAll("\\}", "</br>\\}")
            .replaceAll(",</br>", ",</br>&emsp;")
            .replaceAll("\\{</br>", "\\{</br>&emsp;")
        }

        val keys = Seq("tradingDetails", "contactDetails", "companyDetails", "accountingDetails", "accountsPreparation")

        def exists(json: JsValue): Map[String, Boolean] = {
          keys.map(key => (json \ key).asOpt[JsValue].fold((key, false))(_ => (key, true))).toMap
        }

        for {
          regId <- fetchRegistrationID
          ctRecord <- compRegConnector.retrieveCorporationTaxRegistration(regId)
          heldSubmission <- compRegConnector.fetchHeldSubmission(regId)
        } yield {

          val heldData = heldSubmission.fold[Seq[JsValue]](Seq.empty)(hS => hS \\ "submission").map(formatJsonToString)

          val ctData = exists(ctRecord).filter(_._2).keys.toSeq

          val status: String = (ctRecord \ "status").asOpt[String].fold[String]("No status found for logged in user")(str => str)
          val ct = formatJsonToString(ctRecord)
          Ok(views.html.test.SubmissionStatus(status, ctData, heldData, ct))
        }
  }

  def updateTimepoint(timepoint: String) = Action.async {
    implicit request =>
      compRegConnector.updateTimepoint(timepoint).map(_ => Ok)
  }

  def schedulerFeatureFlag(state: String) = Action.async {
    implicit request =>
      compRegConnector.scheduleFeatureFlag(state).map{ res =>
        Ok(res)
      }
  }

  def simulateDesPost(ackRef: String) = Action.async {
    implicit request =>
      dynStubConnector.simulateDesPost(ackRef).map(_ => Ok)
  }

  def verifyEmail(verified: Boolean) = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>

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

  val testEndpointSummary = Action.async {
    implicit request =>
      Future.successful(Ok(views.html.test.TestEndpointSummary()))
  }


  val fetchPrePopAddresses = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { regId =>
          brConnector.fetchPrePopAddress(regId) map { js =>
            val addresses = Json.fromJson(js)(Address.prePopReads).get
            Ok(views.html.test.PrePopAddresses(addresses))
          }
        }
  }

  val fetchPrePopCompanyContactDetails = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
        registered { regId =>
          brConnector.fetchPrePopContactDetails(regId) map { js =>
            val contactDetails = Json.fromJson(js)(CompanyContactDetailsMongo.prePopReads).get
            Ok(views.html.test.PrePopContactDetails(contactDetails))
          }
        }
  }

  //http:localhost:9870/register-blah/restart
  //register-for-blah/restart

  def dashboardStubbed(payeStatus:String="draft",incorpCTStatus:String ="held",cancelURL:String="true",restartURL:String="true") = Action {
    implicit request =>
      val incorpAndCTDash = IncorpAndCTDashboard(
        incorpCTStatus,
        Some("submissionDate"),
        Some("transactionID"),
        Some("payementRef"),
        Some("crn"),
        Some("submissionDate"),
        Some("ackRef"),
        Some("ackrefStatuses")
      )
      val cancelUrl = if(cancelURL == "true") Some("foo") else None
      val restartUrl = if(restartURL=="true") Some("foo") else None
      val payeLinks = PAYELinks("regURL","otrsURL",restartUrl,cancelUrl)
      val payeDash = PAYEDashboard(payeStatus,Some("lastUpdateDate"),Some("ackrefPaye"),payeLinks)
      val dash = Dashboard(incorpAndCTDash,payeDash,"companyNameStubbed")
      Ok(views.html.reg.Dashboard(dash, coHoURL))
  }

  def handOff6(transactionId: String): Action[AnyContent] = AuthorisedFor(taxRegime = SCRSRegime("test-only/get-s4l"), pageVisibility = GGConfidence).async {
    implicit user =>
      implicit request =>
      registered { regId =>
        val confRefs = ConfirmationReferences(transactionId, "PAY_REF-123456789", "12", "")
        compRegConnector.updateReferences(regId, confRefs) map {
          case ConfirmationReferencesSuccessResponse(refs) => Ok(Json.toJson(refs)(ConfirmationReferences.format))
          case _ => BadRequest
        }
      }
  }
}
