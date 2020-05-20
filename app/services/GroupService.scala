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

package services

import connectors.{CompanyRegistrationConnector, IncorpInfoConnector, KeystoreConnector}
import javax.inject.{Inject, Singleton}
import models._
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import utils.SCRSExceptions

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupService @Inject()(val keystoreConnector: KeystoreConnector,
                             val compRegConnector: CompanyRegistrationConnector,
                             val incorpInfoConnector: IncorpInfoConnector
                            )(implicit ec: ExecutionContext)
  extends CommonService with SCRSExceptions {

  val votingRightsThreshold: Int = 75

  def updateGroupUtr(groupUtr: GroupUTR, groups: Groups, registrationId: String)(implicit hc: HeaderCarrier): Future[Groups] = {
    compRegConnector.updateGroups(registrationId, groups.copy(groupUTR = Some(groupUtr)))
  }

  def retrieveGroups(registrationId: String)(implicit hc: HeaderCarrier): Future[Option[Groups]] = {
    compRegConnector.getGroups(registrationId)
  }

  def dropGroups(registrationId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    compRegConnector.deleteGroups(registrationId)
  }

  def retreiveValidatedTxApiAddress(groups: Groups, registrationId: String)(implicit hc: HeaderCarrier): Future[Option[NewAddress]] = {
    groups.nameOfCompany match {
      case Some(groupCompanyName) =>
        returnAddressFromTxAPI(groupCompanyName, registrationId).flatMap { eitherAddress =>
          eitherAddress.fold(_ => Future.successful(Option.empty[NewAddress]),
            chAddress => {
              val validAddrFromCRValidation = chAddress.fold(Future.successful(Option.empty[NewAddress]))(chroAddress =>
                compRegConnector.validateRegisteredOfficeAddress(registrationId, chroAddress))

              validAddrFromCRValidation.map { newAddress =>
                newAddress
              }
            })
        }
      case None =>
        throw new InternalServerException("[GroupService] [retreiveTxApiAddress] attempted to find txApi address without prerequesite data")
    }
  }

  def dropOldFields(groups: Groups, address: NewAddress, registrationId: String)(implicit hc: HeaderCarrier): Future[Groups] = {
    groups.addressAndType match {
      case Some(addressAndType) if addressAndType.address.mkString != address.mkString && addressAndType.addressType != "ALF" =>
        compRegConnector.updateGroups(registrationId, groups.copy(addressAndType = None, groupUTR = None))
      case _ => Future.successful(groups)
    }
  }

  def createAddressMap(optPrepopAddressAndType: Option[GroupsAddressAndType], address: NewAddress): Map[String, String] = {
    optPrepopAddressAndType match {
      case Some(prepopAddressAndType) =>
        if (prepopAddressAndType.address.mkString == address.mkString) {
          Map(prepopAddressAndType.addressType -> prepopAddressAndType.address.mkString)
        }
        else {
          Map(prepopAddressAndType.addressType -> prepopAddressAndType.address.mkString,
            "TxAPI" -> address.mkString)
        }
      case _ =>
        Map("TxAPI" -> address.mkString)
    }
  }

  def updateGroupAddress(address: GroupsAddressAndType, registrationId: String)(implicit hc: HeaderCarrier): Future[Groups] = {
    retrieveGroups(registrationId).flatMap {
      case Some(groups) =>
        val updatedGroupsBlock =
          if (groups.addressAndType.contains(address)) {
            groups.copy(addressAndType = Some(address))
          }
          else {
            groups.copy(addressAndType = Some(address), groupUTR = None)
          }
        compRegConnector.updateGroups(registrationId, updatedGroupsBlock)
      case None =>
        Future.failed(new InternalServerException("[GroupService] [updateGroupAddress] Missing prerequisite takeover data"))
    }
  }

  def saveTxShareHolderAddress(groups: Groups, registrationID: String)(implicit hc: HeaderCarrier): Future[Either[Exception, Groups]] = {
    retreiveValidatedTxApiAddress(groups, registrationID).flatMap {
      case Some(address) =>
        val updatedGroups = groups.copy(addressAndType = Some(GroupsAddressAndType("CohoEntered", address)))
        compRegConnector.updateGroups(registrationID, updatedGroups).map(groups => Right(groups))
      case None =>
        Future.successful(Left(new InternalServerException("[GroupService] [saveTxShareHolderAddress] Attempted to save TxApiAddress but none was found")))
    }
  }

  def returnListOfShareholders(txId: String)(implicit hc: HeaderCarrier): Future[Either[Exception, List[Shareholder]]] =
    incorpInfoConnector.returnListOfShareholdersFromTxApi(txId).map { list =>
      list.fold[Either[Exception, List[Shareholder]]](
        e => Left(e),
        r => Right(r.collect {
          case shareholder@Shareholder(_, Some(vote), Some(_), Some(_), _) if vote >= votingRightsThreshold => shareholder
        })
      )
    }

  private[services] def fetchTxID(registrationId: String)(implicit hc: HeaderCarrier): Future[String] = {
    compRegConnector.fetchConfirmationReferences(registrationId).map {
      case ConfirmationReferencesSuccessResponse(refs) => refs.transactionId
      case _ => throw new InternalServerException(s"[GroupService] no txId returned for $registrationId")
    }
  }

  def returnAddressFromTxAPI(groupCompanyName: GroupCompanyName, registrationId: String)(implicit hc: HeaderCarrier): Future[Either[Exception, Option[CHROAddress]]] = {
    fetchTxID(registrationId).flatMap { txId =>
      returnListOfShareholders(txId).map { eitherShareholders =>
        eitherShareholders.fold(e => Left(e), iiShareholders =>
          Right(iiShareholders
            .map(shareholder => (shareholder.corporate_name, shareholder.address))
            .find(companyName => companyName._1 == groupCompanyName.name).map(address => address._2)
          )
        )
      }
    }
  }
}
