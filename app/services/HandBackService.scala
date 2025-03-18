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
import connectors.{CompanyRegistrationConnector, KeystoreConnector, S4LConnector}
import models._
import models.handoff._
import utils.Logging
import play.api.libs.json.{Format, JsObject, JsValue}
import repositories._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.binders.RedirectUrl
import utils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
import scala.util.{Failure, Success, Try}

class HandBackServiceImpl @Inject()(val compRegConnector: CompanyRegistrationConnector,
                                    val keystoreConnector: KeystoreConnector,
                                    val s4LConnector: S4LConnector,
                                    val navModelRepo: NavModelRepo,
                                    val jwe: JweCommon,
                                    val appConfig: AppConfig,
                                    val scrsFeatureSwitches: SCRSFeatureSwitches)(implicit val ec: ExecutionContext) extends HandBackService {

  lazy val navModelMongo = navModelRepo.repository
}

case object PayloadNotSavedError extends NoStackTrace

trait HandBackService extends HandOffNavigator with Logging {

  val compRegConnector: CompanyRegistrationConnector
  val jwe : JweCommon
  val keystoreConnector: KeystoreConnector
  val s4LConnector : S4LConnector

  private[services] def decryptHandBackRequest[T](request: String)(f: T => Future[Try[T]])(implicit hc: HeaderCarrier, formats: Format[T]): Future[Try[T]] = {
    request.isEmpty match {
      case true =>
        logger.error(s"[decryptHandBackRequest] Encrypted hand back payload was empty")
        Future.successful(Failure(DecryptionError))
      case false => jwe.decrypt[T](request) match {
        case Success(payload) => f(payload)
        case Failure(ex) =>
          logger.error(s"[decryptHandBackRequest] Payload could not be decrypted: ${ex}")
          Future.successful(Failure(ex))
      }
    }
  }

  def processCompanyNameReverseHandBack(request: String)(implicit hc: HeaderCarrier): Future[Try[JsValue]] = {
    decryptHandBackRequest[JsValue](request){ res =>
      Future.successful(Success(res))
    }
  }

  def processBusinessActivitiesHandBack(request: String)(implicit hc: HeaderCarrier): Future[Try[JsValue]] = {
    decryptHandBackRequest[JsValue](request){ res =>
      Future.successful(Success(res))
    }
  }
  def processGroupsHandBck(request: String)(implicit hc: HeaderCarrier): Future[Try[JsValue]] = {
    decryptHandBackRequest[JsValue](request){ res =>
      Future.successful(Success(res))
    }
  }

  private def validateLink(link: String): Unit = {
    RedirectUrl(link)
  }

  private def validateLinks(links: NavLinks): Unit = {
    validateLink(links.forward)
    validateLink(links.reverse)
  }

  private def validateLinks(links: JumpLinks): Unit = {
    validateLink(links.company_address)
    validateLink(links.company_jurisdiction)
    validateLink(links.company_name)
  }
  private def updateNavModelWithLinksAndCHDataFromCOHO(handOff: String, links: NavLinks, ch: JsObject, oldModel: HandOffNavModel): HandOffNavModel = {
    oldModel.copy(
      receiver = {
        oldModel.receiver.copy(
          nav = oldModel.receiver.nav ++ Map(handOff -> links),
          chData = Some(ch)
        )
      })
  }


  def processCompanyDetailsHandBack(request : String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Try[CompanyNameHandOffIncoming]] = {
    def processNavModel(model: HandOffNavModel, payload: CompanyNameHandOffIncoming): Future[Option[HandOffNavModel]] = {
      val navLinks = payload.links.as[NavLinks]
      val jumpLinks = payload.links.as[JumpLinks]
      validateLinks(navLinks)
      validateLinks(jumpLinks)
      val navModelWithLinksAndCH = updateNavModelWithLinksAndCHDataFromCOHO("2", navLinks, payload.ch, model)
      val updatedNavModel =  navModelWithLinksAndCH.copy(receiver = navModelWithLinksAndCH.receiver.copy(jump = Map(
        "company_name" -> jumpLinks.company_name,
        "company_address" -> jumpLinks.company_address,
        "company_jurisdiction" -> jumpLinks.company_jurisdiction
      )))
      cacheNavModel(updatedNavModel, hc, ec)
    }

    decryptHandBackRequest[CompanyNameHandOffIncoming](request){
      payload =>
        for {
          regID       <- fetchRegistrationID
          model       <- fetchNavModel()
          _           <- processNavModel(model, payload)
          _           <- storeCompanyDetails(payload, regID)
          _           <- compRegConnector.saveTXIDAfterHO2(regID,payload.transactionId)
        } yield {
          Success(payload)
        }
    }

  }

  def processGroupsHandBack(request: String)(implicit hc: HeaderCarrier, ec: ExecutionContext):Future[Try[GroupHandBackModel]] = {
    def processNavModel(model: HandOffNavModel, payload: GroupHandBackModel): Future[Option[HandOffNavModel]] = {
      validateLinks(payload.links)
      val updatedModel = updateNavModelWithLinksAndCHDataFromCOHO("3-1", payload.links, payload.ch, model)
      cacheNavModel(updatedModel, hc, ec)
    }

    decryptHandBackRequest[GroupHandBackModel](request) {
      payload => {
       payload.has_corporate_shareholders match {
         case None => Future.successful(Success(payload))
         case Some(_) => for {
             nm             <- fetchNavModel()
             cachedNavModel <- processNavModel(nm, payload)
           } yield {
             cachedNavModel.fold[Try[GroupHandBackModel]](
               Failure(new Exception("[processGroupsHandBack] - Something went terribly wrong nav model did exist but upon updating it, it no longer exists")))(_ => Success(payload))
           }
         }
       }
      }
    }

  def processSummaryPage1HandBack(request: String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Try[SummaryPage1HandOffIncoming]] = {
    def processNavModel(model: HandOffNavModel, payload: SummaryPage1HandOffIncoming): Future[Option[HandOffNavModel]] = {
      validateLinks(payload.links)
      val updatedModel = updateNavModelWithLinksAndCHDataFromCOHO("4", payload.links, payload.ch, model)
      cacheNavModel(updatedModel, hc, ec)
    }

    decryptHandBackRequest[SummaryPage1HandOffIncoming](request){
      payload =>
        for {
          model       <- fetchNavModel()
          _           <- processNavModel(model, payload)
          storeResult <- storeSimpleHandOff(payload)
        } yield {
          storeResult match {
            case false =>
              logger.error("[processSummaryPage1Handback] CH handoff payload wasn't stored")
              Failure(PayloadNotSavedError)
            case _ => Success(payload)
          }
        }
    }
  }

  def decryptConfirmationHandback(request : String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Try[RegistrationConfirmationPayload]] = {

    def processNavModel(model: HandOffNavModel, payload: RegistrationConfirmationPayload): Future[Option[HandOffNavModel]] = {
      validateLink(getForwardUrl(payload).get)
      val navLinks = NavLinks(getForwardUrl(payload).get,"")
      val updatedModel = updateNavModelWithLinksAndCHDataFromCOHO("5-1", navLinks, payload.ch, model)
      cacheNavModel(updatedModel, hc, ec)
    }

    decryptHandBackRequest[RegistrationConfirmationPayload](request){
      payload =>
        if(payloadHasForwardLinkAndNoPaymentRefs(payload)) {
          for {
            model   <- fetchNavModel()
            _       <- processNavModel(model, payload)
          } yield Success(payload)
        } else {
          Future.successful(Success(payload))
        }
    }
  }

  private[services] def updateCompanyDetails(registrationID: String, handoff: CompanyNameHandOffIncoming)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CompanyDetails] = {
    compRegConnector.retrieveCompanyDetails(registrationID).map{
      case Some(existing) => {
        CompanyDetails.updateFromHandoff(existing, handoff.company_name, handoff.registered_office_address, handoff.jurisdiction)
      }
      case None => {
        CompanyDetails.createFromHandoff(handoff.company_name, handoff.registered_office_address, PPOB("", None), handoff.jurisdiction)
      }
    } flatMap {
      details =>
        compRegConnector.updateCompanyDetails(registrationID, details)
    }
  }

  private[services] def storeCompanyDetails(payload : CompanyNameHandOffIncoming, regId:String)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[CompanyDetails] = {
     updateCompanyDetails(regId, payload)

  }

  private[services] def storeSimpleHandOff(payload : SummaryPage1HandOffIncoming)(implicit hc : HeaderCarrier, ec: ExecutionContext) : Future[Boolean] = {
    for {
      regID <- fetchRegistrationID
    } yield true
  }

  def storeConfirmationHandOff(payload : RegistrationConfirmationPayload, regID : String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ConfirmationReferencesResponse] = {
    compRegConnector.updateReferences(regID, RegistrationConfirmationPayload.getReferences(payload))
  }

  def payloadHasForwardLinkAndNoPaymentRefs(payload : RegistrationConfirmationPayload) : Boolean = {
    getForwardUrl(payload).nonEmpty && payload.payment_reference.isEmpty && payload.payment_amount.isEmpty
  }

  def getForwardUrl(payload: RegistrationConfirmationPayload) : Option[String] = {
    (payload.links \ "forward").asOpt[String]
  }
}