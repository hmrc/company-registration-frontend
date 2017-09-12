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

package services

import config.FrontendAuthConnector
import _root_.connectors._
import models._
import models.auth.Enrolment
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.AuthConnector
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpException}
import utils.{SCRSFeatureSwitches, SCRSExceptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NoStackTrace

object DashboardService extends DashboardService with ServicesConfig {
  val keystoreConnector = KeystoreConnector
  val companyRegistrationConnector = CompanyRegistrationConnector
  val incorpInfoConnector = IncorpInfoConnector
  val payeConnector = PAYEConnector
  val authConnector = FrontendAuthConnector
  val otrsPAYEUrl = getConfString("otrs.url", throw new Exception("Could not find config for key: otrs.url"))
  val payeBaseUrl = getConfString("paye-registration-www.url-prefix", throw new Exception("Could not find config for key: paye-registration-www.url-prefix"))
  val payeUri = getConfString("paye-registration-www.start-url", throw new Exception("Could not find config for key: paye-registration-www.start-url"))
  val featureFlag = SCRSFeatureSwitches
}

sealed trait DashboardStatus
case class DashboardBuilt(d: Dashboard) extends DashboardStatus
case object RejectedIncorp extends DashboardStatus
case object CouldNotBuild extends DashboardStatus

class ComfirmationRefsNotFoundException extends NoStackTrace

trait DashboardService extends SCRSExceptions with CommonService {

  val companyRegistrationConnector : CompanyRegistrationConnector
  val payeConnector: PAYEConnector
  val incorpInfoConnector: IncorpInfoConnector
  val authConnector: AuthConnector
  val otrsPAYEUrl: String
  val payeBaseUrl: String
  val featureFlag: SCRSFeatureSwitches
  val payeUri: String

  def buildDashboard(regId:String)(implicit hc: HeaderCarrier, auth: AuthContext): Future[DashboardStatus] = {
    for {
      incorpCTDash <- buildIncorpCTDashComponent(regId)
      payeDash <- buildPAYEDashComponent(regId)
      hasVatCred <- hasVATEnrollment
    } yield {
      incorpCTDash.status match {
        case "draft" => CouldNotBuild
        case "rejected" => RejectedIncorp
        //case _ => getCompanyName(regId) map(cN => DashboardBuilt(Dashboard(incorpCTDash, payeDash, cN)))
        case _ => DashboardBuilt(Dashboard(incorpCTDash, payeDash, "", hasVatCred)) //todo: leaving company name blank until story gets played to add it back
      }
    }
  }

  private[services] def buildPAYEDashComponent(regId: String)(implicit hc: HeaderCarrier, auth: AuthContext): Future[PAYEDashboard] = {
    import scala.language.implicitConversions
    implicit def toDashboard(paye: PAYEStatus): PAYEDashboard = {
      PAYEDashboard(paye.status, paye.lastUpdate.map(_.toString("dd MMMM yyyy")), paye.ackRef,
        PAYELinks(s"$payeBaseUrl$payeUri", otrsPAYEUrl,paye.restartURL,paye.cancelURL))
    }

    featureFlag.paye.enabled match {
      case true => payeConnector.getStatus(regId) flatMap {
        case PAYESuccessfulResponse(paye) => Future.successful(paye)
        case PAYEErrorResponse => Future.successful(PAYEStatus(PAYEStatuses.UNAVAILABLE, None, None, None,None))
        case PAYENotStarted =>
          hasPAYEEnrollment map {
            case true => PAYEStatus(PAYEStatuses.NOT_ELIGIBLE, None, None,None,None)
            case false => PAYEStatus(PAYEStatuses.NOT_STARTED, None, None,None,None)
          }
      }
      case false => Future.successful(PAYEStatus(PAYEStatuses.NOT_ENABLED, None, None,None,None))
    }
  }

  private[services] def hasPAYEEnrollment(implicit hc: HeaderCarrier, auth: AuthContext): Future[Boolean] = {
    authConnector.getEnrolments[Option[Seq[Enrolment]]](auth) map {
      case Some(e) => e exists (_.key == "IR-PAYE")
      case None => false
    } recover {
      case ex: HttpException =>
        Logger.error(s"[AuthConnector] [getEnrollments] - ${ex.responseCode} was returned - reason : ${ex.message}", ex)
        false
      case ex: Throwable =>
        Logger.error(s"[AuthConnector] [getEnrollments] - An exception was thrown", ex)
        false
    }
  }

  private[services] def hasVATEnrollment(implicit hc: HeaderCarrier, auth: AuthContext): Future[Boolean] = {
    authConnector.getEnrolments[Option[Seq[Enrolment]]](auth) map {
      case Some(e) => e exists (enrolment => enrolment.key == "HMCE-VATDEC-ORG" || enrolment.key == "HMCE-VATVAR-ORG")
      case None => false
    } recover {
      case ex: HttpException =>
        Logger.error(s"[AuthConnector] [getEnrollments] - ${ex.responseCode} was returned - reason : ${ex.message}", ex)
        false
      case ex: Throwable =>
        Logger.error(s"[AuthConnector] [getEnrollments] - An exception was thrown", ex)
        false
    }
  }

  private[services] def buildIncorpCTDashComponent(regId: String)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    companyRegistrationConnector.retrieveCorporationTaxRegistration(regId) flatMap {
      ctReg =>
        (ctReg \ "status").as[String] match {
          case "held" => buildHeld(regId, ctReg)
          case _ => Future.successful(ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(None)))

        }
    }
  }


  private[services] def getCompanyName(regId: String)(implicit hc: HeaderCarrier): Future[String] = {
    for {
      confRefs <- companyRegistrationConnector.fetchConfirmationReferences(regId) map {
        case ConfirmationReferencesSuccessResponse(refs) => refs
        case _ => throw new ComfirmationRefsNotFoundException
      }
      transId = confRefs.transactionId
      companyName <- incorpInfoConnector.getCompanyName(transId)
    } yield companyName
  }

  private[services] def buildHeld(regId: String, ctReg: JsValue)(implicit hc: HeaderCarrier): Future[IncorpAndCTDashboard] = {
    for {
      heldSubmissionDate <- companyRegistrationConnector.fetchHeldSubmissionTime(regId)
      submissionDate = extractSubmissionDate(heldSubmissionDate.get)
    } yield {
      ctReg.as[IncorpAndCTDashboard](IncorpAndCTDashboard.reads(Option(submissionDate)))
    }
  }


  private[services] def extractSubmissionDate(jsonDate: JsValue): String = {

    val dgdt : DateTime = jsonDate.as[DateTime]
    dgdt.toString("dd MMMM yyyy")
  }
}
