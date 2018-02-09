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

package services

import config.{FrontendAppConfig, FrontendConfig}
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import models.handoff.{BusinessActivitiesModel, CompanyNameHandOffModel, HandoffPPOB, _}
import models.{ConfirmationReferencesSuccessResponse, SummaryHandOff}
import play.api.libs.json.{JsObject, JsString, Json}
import repositories.NavModelRepo
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import utils.{Jwe, JweEncryptor, SCRSExceptions}

import scala.concurrent.Future

object HandOffServiceImpl extends HandOffService {
  val keystoreConnector = KeystoreConnector
  val returnUrl = FrontendConfig.self
  val externalUrl = FrontendConfig.selfFull
  val compRegConnector = CompanyRegistrationConnector
  val encryptor = Jwe
  val navModelMongo =  NavModelRepo.repository
  lazy val timeout = FrontendAppConfig.timeoutInSeconds.toInt
  lazy val timeoutDisplayLength = FrontendAppConfig.timeoutDisplayLength.toInt
}

trait HandOffService extends CommonService with SCRSExceptions with ServicesConfig with HandOffNavigator {

  val keystoreConnector: KeystoreConnector
  val returnUrl: String
  val externalUrl: String
  val compRegConnector: CompanyRegistrationConnector
  val encryptor : JweEncryptor
  val timeout : Int
  val timeoutDisplayLength : Int

  def buildHandOffUrl(url: String, payload: String) = url match {
    case u if u.endsWith("request=") => s"$url$payload"
    case u if u.endsWith("?") => s"${url}request=$payload"
    case u if u.endsWith("&") => s"${url}request=$payload"
    case u if u.contains("?") => s"$url&request=$payload"
    case _ => s"$url?request=$payload"
  }

  def getURL(path : String) = s"$returnUrl$path"

  def buildBusinessActivitiesPayload(regId: String, externalId : String)(implicit hc : HeaderCarrier) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel() map {
      implicit model =>
        (forwardTo(3), hmrcLinks(3), model.receiver.chData)
    }

    for {
      Some(addressData) <- compRegConnector.retrieveCompanyDetails(regId)
      (url, links, chData) <- navModel
    } yield {
      val payload = BusinessActivitiesModel(
        authExtId = externalId,
        regId = regId,
        ppob = Some(HandoffPPOB.fromCorePPOB(addressData.pPOBAddress)),
        ch = chData,
        hmrc = JsObject(Seq()),
        links = links
      )
      encryptor.encrypt[BusinessActivitiesModel](payload) map { (url, _) }
    }
  }

  def companyNamePayload(regId: String, email : String, name : String, extID : String)
                        (implicit hc : HeaderCarrier) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel(canCreate = true) map {
      implicit model =>
        (forwardTo(1), hmrcLinks(1), model.receiver.chData)
    }

    for {
      (url, links, chData) <- navModel
    } yield {
      val payload = CompanyNameHandOffModel(
        email_address = email,
        is_verified_email_address = None,
        journey_id = Some(regId),
        user_id = extID,
        name = name,
        hmrc = Json.obj(),
        session = renewSessionObject,
        ch = chData,
        links = links)
      encryptor.encrypt[CompanyNameHandOffModel](payload) map { (url, _) }
    }
  }

  lazy val renewSessionObject: JsObject = {
    JsObject(Map(
      "timeout" -> Json.toJson(timeout - timeoutDisplayLength),
      "keepalive_url" -> Json.toJson(s"$externalUrl${controllers.reg.routes.SignInOutController.renewSession().url}"),
      "signedout_url" -> Json.toJson(s"$externalUrl${controllers.reg.routes.SignInOutController.destroySession().url}")
    ))
  }

  def buildLinksObject(navLinks : NavLinks, jumpLinks: Option[JumpLinks]) : JsObject = {
    val obj = Json.obj("forward" -> navLinks.forward,"reverse" -> navLinks.reverse)

    if (jumpLinks.isDefined) {
      obj.
        +("company_name" -> JsString(jumpLinks.get.company_name)).
        +("company_address" -> JsString(jumpLinks.get.company_address)).
        +("company_jurisdiction" -> JsString(jumpLinks.get.company_jurisdiction))
    } else {
      obj
    }
  }

  def buildBackHandOff(externalID : String)(implicit hc : HeaderCarrier) : Future[BackHandoff] = {
    for {
      regID <- fetchRegistrationID
      navModel <- fetchNavModel()
    } yield {
      BackHandoff(
        externalID,
        regID,
        navModel.receiver.chData.get,
        Json.obj(),
        Json.obj()
      )
    }
  }

  def summaryHandOff(externalID : String)(implicit hc : HeaderCarrier) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel() map {
      implicit model =>
        (forwardTo(5), hmrcLinks(5), model.receiver.chData)
    }

    for {
      journeyID <- fetchRegistrationID
      _ <- updateRegistrationProgressHO5(journeyID)
      (url, links, chData) <- navModel
    } yield {
      val payloadModel =
        SummaryHandOff(
          externalID,
          journeyID,
          Json.obj(),
          chData,
          Json.toJson[NavLinks](links).as[JsObject]
        )
      encryptor.encrypt[SummaryHandOff](payloadModel).map((url, _))
    }
  }

  def buildPaymentConfirmationHandoff(externalID : Option[String])(implicit hc : HeaderCarrier): Future[Option[(String,String)]] = {
    def navModel = {
      fetchNavModel() map {
        implicit model =>
          (forwardTo("5-2"), hmrcLinks("5-2"), model.receiver.chData)
      }
    }
    for {
      regId                     <- fetchRegistrationID
      (url, navLinks, chData)   <- navModel
      ctReference               <- compRegConnector.fetchConfirmationReferences(regId) map {
        case ConfirmationReferencesSuccessResponse(refs) => refs.acknowledgementReference
        case _ => throw new ConfirmationRefsNotFoundException
      }
    } yield {
      val payloadModel = PaymentHandoff(
        externalID.get,
        regId,
        ctReference,
        Json.obj(),
        chData.get,
        Json.obj("forward" -> navLinks.forward)
      )
      encryptor.encrypt[PaymentHandoff](payloadModel).map((url,_))
    }
  }

  private[services] def updateRegistrationProgressHO5(registrationId: String)(implicit hc: HeaderCarrier) = {
    import constants.RegistrationProgressValues.HO5
    compRegConnector.updateRegistrationProgress(registrationId, HO5)
  }
}
