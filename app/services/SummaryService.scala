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

import connectors._
import controllers.reg.ControllerErrorHandler
import models.SummaryListRowUtils.{optSummaryListRowBoolean, optSummaryListRowSeq, optSummaryListRowString}
import models._
import play.api.i18n.{Messages, MessagesApi}
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request}
import repositories.NavModelRepo
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{SummaryList, SummaryListRow}
import uk.gov.hmrc.http.HeaderCarrier
import utils.{JweCommon, SCRSFeatureSwitches}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SummaryService @Inject()(val authConnector: PlayAuthConnector,
                               val s4LConnector: S4LConnector,
                               val compRegConnector: CompanyRegistrationConnector,
                               val keystoreConnector: KeystoreConnector,
                               val metaDataService: MetaDataService,
                               val takeoverService: TakeoverService,
                               val handOffService: HandOffService,
                               val navModelRepo: NavModelRepo,
                               val scrsFeatureSwitches: SCRSFeatureSwitches,
                               val jwe: JweCommon,
                               val controllerComponents: MessagesControllerComponents,
                               val controllerErrorHandler: ControllerErrorHandler,
                               val messagesApi: MessagesApi
                              )(implicit ec: ExecutionContext) {

  def getCompletionCapacity(regId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SummaryList] = {
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      completionCapacity <- metaDataService.getApplicantData(regId)
    } yield {
      completionCapacitySectionSummary(completionCapacity)
    }
  }

  def getAccountingDates(regId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SummaryList] = {
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      tradingDetails <- compRegConnector.retrieveTradingDetails(regId)
      accountingDates <- compRegConnector.retrieveAccountingDetails(regId)
    } yield {
      val accountDates: AccountingDetails = accountingDates match {
        case AccountingDetailsSuccessResponse(response) => response
        case _ => throw new Exception("could not find company accounting details")
      }

      accountingDatesSectionSummary(accountDates, tradingDetails.getOrElse(throw new Exception("could not find trading details")))
    }
  }

  def getTakeoverBlock(regId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SummaryList] = {
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      takeoverBlock <- takeoverService.getTakeoverDetails(regId)
    } yield {
      takeoverSectionSummary(takeoverBlock.getOrElse(throw new Exception("could not find takeover block")))
    }
  }

  def getContactDetailsBlock(regId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SummaryList] = {
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      contactDetails <- compRegConnector.retrieveContactDetails(regId)
      companyDetails <- compRegConnector.retrieveCompanyDetails(regId)
    } yield {
      val companyContactDetails: CompanyContactDetails = contactDetails match {
        case CompanyContactDetailsSuccessResponse(response) => response
        case _ => throw new Exception("could not find company contact details")
      }
      contactDetailsSectionSummary(companyContactDetails, companyDetails)
    }
  }

  def getCompanyDetailsBlock(regId: String)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[SummaryList] = {
    implicit val messages: Messages = messagesApi.preferred(request)
    for {
      companyDetails <- compRegConnector.retrieveCompanyDetails(regId)
    } yield {
      companyDetailsSectionSummary(companyDetails)
    }
  }

  private[services] def completionCapacitySectionSummary(completionCapacity: AboutYouChoice)(implicit messages: Messages): SummaryList =
    SummaryList(buildCompletionCapacitySection(completionCapacity))

  private[services] def companyDetailsSectionSummary(companyDetails: Option[CompanyDetails])(implicit messages: Messages): SummaryList =
    SummaryList(buildCompanyDetailsSection(companyDetails))

  private[services] def takeoverSectionSummary(takeoverBlock: TakeoverDetails)(implicit messages: Messages): SummaryList =
    SummaryList(buildTakeoverSection(takeoverBlock))

  private[services] def accountingDatesSectionSummary(accountDetails: AccountingDatesModel, tradingDetail: TradingDetails)(implicit messages: Messages): SummaryList =
    SummaryList(buildAccountingDatesSection(accountDetails, tradingDetail))

  private[services] def contactDetailsSectionSummary(contactDetails: CompanyContactDetails, companyDetails: Option[CompanyDetails])(implicit messages: Messages): SummaryList =
    SummaryList(buildCompanyContactDetailsSection(contactDetails, companyDetails))

  private[services] def buildCompletionCapacitySection(aboutYouChoice: AboutYouChoice)(implicit messages: Messages): Seq[SummaryListRow] = {

    val companyContactDetails = optSummaryListRowString(
      messages("page.reg.AboutYou.description"),
      Some(aboutYouChoice.completionCapacity.capitalize),
      Some(controllers.reg.routes.CompletionCapacityController.show.url)
    )
    Seq(
      companyContactDetails
    ).flatten
  }


  private[services] def buildCompanyDetailsSection(companyDetails: Option[CompanyDetails])(implicit messages: Messages): Seq[SummaryListRow] = {

    val companyName = optSummaryListRowString(
      messages("page.reg.summary.companyNameText"),
      companyDetails.map(_.companyName),
      Some(controllers.reg.routes.SummaryController.summaryBackLink("company_name").url)
    )

    val companyAddress = optSummaryListRowString(
      messages("page.reg.summary.ROAddressText"),
      companyDetails.map(_.cHROAddress.toString),
      Some(controllers.reg.routes.SummaryController.summaryBackLink("company_name").url)
    )

    val registeredJurisdiction = optSummaryListRowString(
      messages("page.reg.summary.jurisdictionText"),
      companyDetails.map(_.jurisdiction),
      Some(controllers.reg.routes.SummaryController.summaryBackLink("company_name").url)
    )

    val registeredLocation = optSummaryListRowString(
      messages("page.reg.summary.PPOBAddressText"),
      if (companyDetails.map(_.pPOBAddress.address.map(_.toString).get) == companyDetails.map(_.cHROAddress.toString)) {
        Some(messages("page.reg.summary.PPOBSameAsRO"))
      } else {
        companyDetails.map(_.pPOBAddress.address.map(_.toString).get)
      },
      Some(controllers.reg.routes.PPOBController.show.url)
    )

    Seq(
      companyName,
      companyAddress,
      registeredJurisdiction,
      registeredLocation
    ).flatten
  }

  private[services] def buildCompanyContactDetailsSection(contactDetails: CompanyContactDetails, companyDetails: Option[CompanyDetails])(implicit messages: Messages): Seq[SummaryListRow] = {

    val companyContactDetails = optSummaryListRowSeq(
      messages("page.reg.summary.companyContact", companyDetails.map(_.companyName).get),
      Some(Seq() ++ contactDetails.contactEmail ++ contactDetails.contactDaytimeTelephoneNumber ++ contactDetails.contactMobileNumber),
      Some(controllers.reg.routes.CompanyContactDetailsController.show.url)
    )
    Seq(
      companyContactDetails
    ).flatten
  }

  private[services] def buildAccountingDatesSection(accountDetails: AccountingDatesModel, tradingDetails: TradingDetails)(implicit messages: Messages): Seq[SummaryListRow] = {

    val accountingDates = optSummaryListRowString(
      messages("page.reg.summary.startDate"),
      if (accountDetails.crnDate == "WHEN_REGISTERED") {
        Some(messages("page.reg.summary.dateRegistered"))
      } else if (accountDetails.crnDate == "NOT_PLANNING_TO_YET") {
        Some(messages("page.reg.summary.notPlanningToStartYet"))
      }
      else {
        accountDetails.toSummaryDate
      },
      Some(controllers.reg.routes.AccountingDatesController.show.url)
    )

    val overseasInvestments = optSummaryListRowString(
      messages("page.reg.summary.tradingDetails"),
      if(tradingDetails.regularPayments == "false") {
        Some(messages("page.reg.ct61.radioNoLabel"))
      } else {Some(messages("page.reg.ct61.radioYesLabel"))},
      Some(controllers.reg.routes.TradingDetailsController.show.url)
    )
    Seq(
      accountingDates,
      overseasInvestments
    ).flatten
  }


  private[services] def buildTakeoverSection(takeoverBlock: TakeoverDetails)(implicit messages: Messages): Seq[SummaryListRow] = {

    val replacingAnotherBusiness = optSummaryListRowBoolean(
      messages("page.reg.summary.takeovers.replacingBusiness"),
      Some(takeoverBlock.replacingAnotherBusiness),
      Some(controllers.takeovers.routes.ReplacingAnotherBusinessController.show.url)
    )

    val businessName = optSummaryListRowString(
      messages("page.reg.summary.takeovers.otherBusinessName"),
      takeoverBlock.businessName,
      Some(controllers.takeovers.routes.OtherBusinessNameController.show.url)
    )

    val takeoverAddress = optSummaryListRowString(
      messages("page.reg.summary.takeovers.businessTakeOverAddress", takeoverBlock.businessName.getOrElse("")),
      takeoverBlock.businessTakeoverAddress.map(_.toString),
      Some(controllers.takeovers.routes.OtherBusinessAddressController.show.url)
    )

    val whoAgreesTakeover = optSummaryListRowString(
      messages("page.reg.summary.takeovers.personAgreed"),
      takeoverBlock.previousOwnersName,
      Some(controllers.takeovers.routes.WhoAgreedTakeoverController.show.url)
    )

    val previousOwnerAddress = optSummaryListRowString(
      messages("page.reg.summary.takeovers.previousOwnersAddress", takeoverBlock.previousOwnersName.getOrElse("")),
      takeoverBlock.previousOwnersAddress.map(_.toString),
      Some(controllers.takeovers.routes.PreviousOwnersAddressController.show.url)
    )

    Seq(
      replacingAnotherBusiness,
      businessName,
      takeoverAddress,
      whoAgreesTakeover,
      previousOwnerAddress
    ).flatten
  }
}
