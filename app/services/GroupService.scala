/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.{CompanyRegistrationConnector, KeystoreConnector}

import connectors.IncorpInfoConnector
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.http.HeaderCarrier
import utils.SCRSExceptions
import models._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class GroupServiceImpl @Inject()(val keystoreConnector: KeystoreConnector,
                                 val compRegConnector: CompanyRegistrationConnector,
                                 val incorpInfoConnector: IncorpInfoConnector) extends GroupService


trait GroupService extends CommonService with SCRSExceptions {

  val compRegConnector: CompanyRegistrationConnector
  val incorpInfoConnector: IncorpInfoConnector
  val votingRightsThreshold: Int = 75

  def returnListOfShareholdersFromTxApi(txId: String)(implicit hc:HeaderCarrier): Future[Either[Exception,List[Shareholder]]] = incorpInfoConnector.returnListOfShareholdersFromTxApi(txId).map {
    list => list.fold[Either[Exception,List[Shareholder]]](e => Left(e),r => Right(r.collect {
      case s@Shareholder(_, Some(vote), Some(divid), Some(cap), _) if vote >= votingRightsThreshold => s
    }))
  }
  def returnListOfEligibleShareholdersFromCRDesValidationEndpoint(listOfShareholders: List[Shareholder])(implicit hc:HeaderCarrier): Future[List[String]] = {
    compRegConnector.shareholderListValidationEndpoint(listOfShareholders.map(_.corporate_name))
  }

  def returnAddressOfShareholder(listOfShareholders: List[Shareholder], shareHolderCompanyName: String)(implicit hc:HeaderCarrier): Option[CHROAddress] = {
    listOfShareholders.map(s => (s.corporate_name, s.address) ).find(cn => cn._1 == shareHolderCompanyName).map(addr => addr._2)
  }

  private[services] def fetchTxID(regID:String)(implicit hc:HeaderCarrier):Future[String] = {
    compRegConnector.fetchConfirmationReferences(regID).map {
      case ConfirmationReferencesSuccessResponse(refs) => refs.transactionId
      case _ => throw new Exception(s"[GroupService] no txId returned for $regID")
    }
  }

  def returnAddressFromTxAPIBasedOnShareholderName(groupsInCR: Groups, regId: String)(implicit hc:HeaderCarrier): Future[Either[Exception,Option[CHROAddress]]] = {
    fetchTxID(regId).flatMap { txid =>
      returnListOfShareholdersFromTxApi(txid).map {
        eitherShareholders =>
          eitherShareholders.fold(e => Left(e), iiShareholders =>
            Right(returnAddressOfShareholder(iiShareholders, groupsInCR.nameOfCompany.get.name)))
      }
    }
  }

  private def returnValidNewAddressFromCRUsingTXApiAddress(regID: String, cHROAddress: CHROAddress)(implicit hc:HeaderCarrier) : Future[Option[NewAddress]] =
      compRegConnector.checkValidShareHolderAddressFromCoho(regID,cHROAddress)

  def returnAddressFromTXAPIValidateAndMatchWithCR(groupsInCR: Groups, regId: String)(implicit hc:HeaderCarrier):Future[(Option[NewAddress], Option[Boolean])] =  {
        returnAddressFromTxAPIBasedOnShareholderName(groupsInCR, regId).flatMap {
          eitherAddress =>
            eitherAddress.fold(_ => Future.successful((Option.empty[NewAddress], Option.empty[Boolean])),
              chAddress => {
                val validAddrFromCRValidation = chAddress.fold(Future.successful(Option.empty[NewAddress]))(chroAddress =>
                returnValidNewAddressFromCRUsingTXApiAddress(regId, chroAddress))

                validAddrFromCRValidation.map { newAddress =>
                val doesItMatchCRAddress: Option[Boolean] = (newAddress, groupsInCR.addressAndType)  match {
                  case (addrOpt, Some(GroupsAddressAndType(_, crAddress))) if addrOpt.exists(_.mkString == crAddress.mkString) => Some(true)
                  case _ => Some(false)
                }
                  (newAddress, doesItMatchCRAddress)
         }})
      }
    }
  private def dropOptionalBlocksBasedOnAddressChanging(groupsInCR: Groups, regId:String, addressChanged: Option[Boolean])(implicit hc:HeaderCarrier):Future[Groups] = {
    (groupsInCR, addressChanged) match {
      case (Groups(_, _, Some(address), _), Some(false)) if address.addressType != "ALF" =>
        compRegConnector.updateGroups(regId, Groups(groupsInCR.groupRelief, groupsInCR.nameOfCompany, None, None))
      case _ => Future.successful(groupsInCR)
    }
  }

  def returnMapOfAddressesMatchDropAndReturnUpdatedGroups(groupsInCR: Groups, regId:String)(implicit hc:HeaderCarrier) :Future[(Map[String,String], Groups)] = {
    for {
      (optNewAddress, addressMatched) <- returnAddressFromTXAPIValidateAndMatchWithCR(groupsInCR, regId)
      updatedGroups <- dropOptionalBlocksBasedOnAddressChanging(groupsInCR, regId, addressMatched)
    } yield {
      val mapOfAddresses = returnMapOfAddresses(addressMatched,updatedGroups.addressAndType, optNewAddress)
      (mapOfAddresses,updatedGroups)
    }
  }

  private[services] def returnMapOfAddresses( addressMatched: Option[Boolean],
                              updateGroupsAddressAndType: Option[GroupsAddressAndType],
                              optNewAddressFromTxApi: Option[NewAddress]):Map[String,String] = {
    (addressMatched, updateGroupsAddressAndType) match {
      case (Some(true), Some(GroupsAddressAndType("ALF", addr))) => Map("ALF" -> addr.mkString)
      case (Some(false), Some(GroupsAddressAndType("ALF", addr))) if optNewAddressFromTxApi.isDefined => Map("ALF" -> addr.mkString, "TxAPI" -> optNewAddressFromTxApi.get.mkString)
      case (Some(false), Some(GroupsAddressAndType("ALF", addr))) if optNewAddressFromTxApi.isEmpty => Map("ALF" -> addr.mkString)
      case (Some(true), Some(GroupsAddressAndType(addressType, addr))) => Map(addressType -> addr.mkString)
      case _ if optNewAddressFromTxApi.isDefined => Map("TxAPI" -> optNewAddressFromTxApi.get.mkString)
      case _ => Map.empty[String, String]
    }
  }


   def dropGroups(regID:String)(implicit hc:HeaderCarrier):Future[Boolean] = {
    compRegConnector.deleteGroups(regID)
  }
  def potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList(regID:String)(implicit hc:HeaderCarrier):Future[List[Shareholder]] = {
    fetchTxID(regID).flatMap { txId =>
      returnListOfShareholdersFromTxApi(txId).flatMap {
        eitheriiShareholders =>
          eitheriiShareholders.fold(e => Future.successful(List.empty), listShareholders => {
            if (listShareholders.isEmpty) dropGroups(regID).map(_ => listShareholders) else Future.successful(listShareholders)
          }
          )
      }
    }
  }

  def checkGroupNameMatchAndPotentiallyDropOptionalBlocks(groupsInCR: Groups, regId: String)(implicit hc:HeaderCarrier): Future[(List[String], Groups)] = {
    fetchTxID(regId).flatMap{ txId =>
      returnListOfShareholdersFromTxApi(txId).flatMap{ eitherShareholders =>
        eitherShareholders.fold(_ => Future.successful((List.empty, groupsInCR)),
          iiShareholders => {
            returnListOfEligibleShareholdersFromCRDesValidationEndpoint(iiShareholders).flatMap {
              validShareholdersFromCr =>
                val (isOther,name,nameExistsInList) = isOtherNameNameExistsInList(validShareholdersFromCr,Some(groupsInCR))
                if (!nameExistsInList && !isOther) {
                  compRegConnector.updateGroups(regId, Groups(groupsInCR.groupRelief, None, None, None)).map(updatedGroups =>
                    (validShareholdersFromCr.distinct, updatedGroups))
                } else {
                  Future.successful(validShareholdersFromCr.distinct.filterNot(shName => shName == name.getOrElse("") && !isOther), groupsInCR)
                }
            }
          })
      }
    }
  }

  private def isOtherNameNameExistsInList(validShareholdersFromCr: List[String], groups : Option[Groups]): (Boolean,Option[String],Boolean) = {
    groups.fold[(Boolean,Option[String],Boolean)]((false,None,false)) { groupsInCR =>
      val isOther = groupsInCR.nameOfCompany.exists(_.nameType == "Other")
      val name = groupsInCR.nameOfCompany.map(_.name)
      val nameExistsInList = name.exists(name => validShareholdersFromCr.contains(name))

      (isOther, name, nameExistsInList)
    }
  }

  def hasDataChangedIfSoDropGroups(listOfIIShareholders: List[Shareholder], regID: String)(implicit hc:HeaderCarrier): Future[List[Shareholder]] = {
    for {
      optGroups     <- retrieveGroups(regID)
      eligibleNames <- returnListOfEligibleShareholdersFromCRDesValidationEndpoint(listOfIIShareholders)
      (isOther,name,nameExistsInList) = isOtherNameNameExistsInList(eligibleNames,optGroups)
      _             <- (nameExistsInList, isOther, optGroups.flatMap(_.nameOfCompany).isDefined) match {
        case (false, false, true) => dropGroups(regID)
        case _ => Future.successful(false)
      }
    } yield {
      listOfIIShareholders
    }
  }

  def updateGroupRelief(groupReliefSubmitted : Boolean, existingGroupsBlock: Option[Groups], registrationID: String)(implicit hc: HeaderCarrier) : Future[Groups] = {
    val defaultBlock = Groups(groupReliefSubmitted,None,None,None)
    val updatedBlock = if(existingGroupsBlock.isEmpty || !groupReliefSubmitted) {
      defaultBlock
    } else {
      existingGroupsBlock.get.copy(groupRelief = groupReliefSubmitted)
    }

    compRegConnector.updateGroups(registrationID, updatedBlock)

  }
  private[services] def isGroupCompanyNameSameAsOneInCR(gcn: GroupCompanyName, groups: Groups): Boolean = groups.nameOfCompany.contains(gcn)
  private[services] def isGroupAddressSameAsOneInCR(gaat: GroupsAddressAndType, groups: Groups): Boolean = groups.addressAndType.contains(gaat)

  def updateGroupName(groupNameSubmitted : GroupCompanyName, existingGroupsBlock: Groups, registrationID: String)(implicit hc: HeaderCarrier) : Future[Groups] = {

    val updatedGroupsBlock =
      if (isGroupCompanyNameSameAsOneInCR(groupNameSubmitted, existingGroupsBlock)) {
        existingGroupsBlock.copy(nameOfCompany = Some(groupNameSubmitted)) }
      else {
        existingGroupsBlock.copy(nameOfCompany = Some(groupNameSubmitted), addressAndType = None, groupUTR = None)
      }
    compRegConnector.updateGroups(registrationID,updatedGroupsBlock)
  }

  def updateGroupAddress(groupShareHolderAddress : GroupsAddressAndType, existingGroupsBlock: Groups, registrationID: String)(implicit hc: HeaderCarrier) : Future[Groups] = {

    val updatedGroupsBlock =
      if (isGroupAddressSameAsOneInCR(groupShareHolderAddress,existingGroupsBlock)) {
        existingGroupsBlock.copy(addressAndType = Some(groupShareHolderAddress))
      }
      else {
        existingGroupsBlock.copy(addressAndType = Some(groupShareHolderAddress), groupUTR = None)
      }
    compRegConnector.updateGroups(registrationID,updatedGroupsBlock)
  }

  def updateGroupUtr(groupUtrSubmitted : GroupUTR, existingGroupsBlock: Groups, registrationID: String)(implicit hc: HeaderCarrier) : Future[Groups] = {
    val updatedGroupsBlock = existingGroupsBlock.copy(groupUTR = Some(groupUtrSubmitted))
    compRegConnector.updateGroups(registrationID,updatedGroupsBlock)
  }

  def retrieveGroups(registrationID : String)(implicit hc: HeaderCarrier) : Future[Option[Groups]] = {
    compRegConnector.getGroups(registrationID)
  }

  def groupsUserSkippedPage(optGroups: Option[Groups], groupPage :GroupPageEnum.Value)(f: Groups => Future[Result]): Future[Result] = {
    val isEligibleToContinue = (optGroups, groupPage) match {
      case(Some(_),GroupPageEnum.shareholderNamePage) => true
      case(Some(Groups(_,Some(_),_,_)),GroupPageEnum.shareholderAddressPage) => true
      case(Some(Groups(_,Some(_),Some(_),_)),GroupPageEnum.utrPage) => true
      case userhasSkippedGroupPages => false
    }
    if(isEligibleToContinue) {
      f(optGroups.get)
    } else {
      Future.successful(Redirect(controllers.reg.routes.SignInOutController.postSignIn()))
    }
  }

  def saveTxShareHolderAddress(existingGroupsBlock: Groups, registrationID: String)(implicit hc: HeaderCarrier): Future[Either[Exception,Groups]] = {
    returnAddressFromTXAPIValidateAndMatchWithCR(existingGroupsBlock, registrationID).flatMap { addr =>
      val newAddressOpt = addr._1
      if (newAddressOpt.isDefined) {
        val updatedGroupsBlock = existingGroupsBlock.copy(addressAndType = Some(GroupsAddressAndType("CohoEntered", newAddressOpt.get)))
        compRegConnector.updateGroups(registrationID,updatedGroupsBlock).map(grps => Right(grps))
      } else {
        Future.successful(Left(new Exception("Address validation on CR returned nothing back on submit")))
      }
    }
  }
}
object GroupPageEnum extends Enumeration {
  val utrPage = Value
  val groupReliefPage = Value
  val shareholderAddressPage = Value
  val shareholderNamePage = Value
}
