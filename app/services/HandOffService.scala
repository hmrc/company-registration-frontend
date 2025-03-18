/*
 * Copyright 2023 HM Revenue & Customs
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

import javax.inject.Inject
import config.AppConfig
import connectors.{CompanyRegistrationConnector, KeystoreConnector}
import models.handoff.{BusinessActivitiesModel, CompanyNameHandOffModel, HandoffPPOB, _}
import models.{ConfirmationReferencesSuccessResponse, Groups}
import play.api.libs.json.{JsObject, JsString, Json}
import repositories.NavModelRepo
import uk.gov.hmrc.http.HeaderCarrier
import utils._

import scala.concurrent.{ExecutionContext, Future}

class HandOffServiceImpl @Inject()(

                                   val keystoreConnector: KeystoreConnector,
                                   val encryptor: JweCommon,
                                   val appConfig: AppConfig,
                                   val navModelRepo: NavModelRepo,
                                   val compRegConnector: CompanyRegistrationConnector,
                                   val scrsFeatureSwitches: SCRSFeatureSwitches)(implicit val ec: ExecutionContext) extends HandOffService {

  lazy val returnUrl                 = appConfig.self
  lazy val externalUrl               = appConfig.selfFull
  lazy val navModelMongo             = navModelRepo.repository
  lazy val timeout              = appConfig.timeoutInSeconds.toInt
  lazy val timeoutDisplayLength = appConfig.timeoutDisplayLength.toInt
}

trait HandOffService extends HandOffNavigator {

  val returnUrl: String
  val externalUrl: String
  val compRegConnector: CompanyRegistrationConnector
  val encryptor : JweCommon
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

  private[services] val groupsCheckerForPSCHandOff = (groups: Option[Groups]) => groups.map {
    case g@Groups(true, name, addr, utr) if name.isDefined && addr.isDefined && utr.isDefined => g
    case g@Groups(true, name, addr, utr) => throw new Exception(s"[groupsChecker] Groups block incomplete, user skipped journey ${name.isDefined}, ${addr.isDefined}, ${utr.isDefined}")
    case g@Groups(false,_, _,_) => Groups(groupRelief = false,None,None,None)
  }
 private[services] val buildTheStaticJumpLinksForGroupsBasedOnGroupsData = (groups: Option[Groups]) => groups.map(
    og => if(!og.groupRelief) {
      JumpLinksForGroups(
        groupReliefUrl
        , None, None, None
      )
    } else {
      JumpLinksForGroups(
       groupReliefUrl,
        Some(groupAddressUrl),
        Some(groupNameUrl),
        Some(groupUTRUrl)
      )
    }
  )
  private[services] val buildParentCompanyNameOutOfGroups = (groups: Option[Groups]) => {
    groups.flatMap{
      og => if(og.groupRelief) {
        Some(ParentCompany(
          og.nameOfCompany.get.name,
          og.addressAndType.get.address,
          og.groupUTR.get.UTR))
      } else {
        None
      }
    }
  }

  def buildPSCPayload(regId: String, externalId: String, groups: Option[Groups], currentLanguage: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(String,String)]] = {
    val navModel = fetchNavModel() map {
      implicit model =>
        (forwardTo("3-2"), hmrcLinks("3-2"), model.receiver.chData)
    }

    navModel.map { navmodel =>
      val (url, links, chData) = navmodel
       val groupsInfluenced3bor3b1BackHandOff = (groups: Option[Groups]) => {
       links.copy(reverse = groups.fold(regularPaymentsBackUrl)(groupsExists =>
         groupBackHandBackUrl
       ))
        }

      val groupsUpdatedAndChecked =  groupsCheckerForPSCHandOff(groups)
      val jumpLinks = buildTheStaticJumpLinksForGroupsBasedOnGroupsData(groupsUpdatedAndChecked)
      val parentCompanyName = buildParentCompanyNameOutOfGroups(groupsUpdatedAndChecked)

      encryptor.encrypt[PSCHandOff](PSCHandOff(
        externalId,
        regId,
        Json.obj(),
        chData,
        currentLanguage,
        groupsInfluenced3bor3b1BackHandOff(groups),
        groupsUpdatedAndChecked.isDefined,
        groupsUpdatedAndChecked.map(_.groupRelief),
        buildParentCompanyNameOutOfGroups(groupsUpdatedAndChecked),
        jumpLinks
      )) map {
        (url, _)
      }
    }
  }

  def buildBusinessActivitiesPayload(regId: String, externalId : String, currentLang: String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel() map {
      implicit model =>
        (forwardTo(3), hmrcLinks("3"), model.receiver.chData)
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
        language = currentLang,
        links = links
      )
      encryptor.encrypt[BusinessActivitiesModel](payload) map { (url, _) }
    }
  }

  def companyNamePayload(regId: String, email : String, name : String, extID : String, currentLang: String)
                        (implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel(canCreate = true) map {
      implicit model =>
        (forwardTo(1), hmrcLinks("1"), model.receiver.chData)
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
        language = currentLang,
        links = links)
      encryptor.encrypt[CompanyNameHandOffModel](payload) map { (url, _) }
    }
  }

  lazy val renewSessionObject: JsObject = {
    JsObject(Map(
      "timeout" -> Json.toJson(timeout - timeoutDisplayLength),
      "keepalive_url" -> Json.toJson(renewSessionUrl),
      "signedout_url" -> Json.toJson(destorySessionUrl)
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

  def buildBackHandOff(externalID : String, currentLanguage: String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[BackHandoff] = {
    for {
      regID <- fetchRegistrationID
      navModel <- fetchNavModel()
    } yield {
      BackHandoff(
        externalID,
        regID,
        navModel.receiver.chData.getOrElse(throw new Exception("could not get chData from navModel in the buildBackHandOff method")),
        Json.obj(),
        currentLanguage,
        Json.obj()
      )
    }
  }

  def summaryHandOff(externalID : String, currentLanguage: String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Option[(String, String)]] = {
    val navModel = fetchNavModel() map {
      implicit model =>
        (forwardTo(5), hmrcLinks("5"), model.receiver.chData)
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
          currentLanguage,
          chData,
          Json.toJson[NavLinks](links).as[JsObject]
        )
      encryptor.encrypt[SummaryHandOff](payloadModel).map((url, _))
    }
  }

  def buildPaymentConfirmationHandoff(externalID : Option[String], currentLanguage: String)(implicit hc : HeaderCarrier, ec: ExecutionContext): Future[Option[(String,String)]] = {
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
        currentLanguage,
        Json.obj("forward" -> navLinks.forward)
      )
      encryptor.encrypt[PaymentHandoff](payloadModel).map((url,_))
    }
  }

  private[services] def updateRegistrationProgressHO5(registrationId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    import constants.RegistrationProgressValues.HO5
    compRegConnector.updateRegistrationProgress(registrationId, HO5)
  }
}