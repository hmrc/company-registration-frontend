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
import mocks.SCRSMocks
import models._
import models.connectors.ConfirmationReferences
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Results
import play.api.test.Helpers._
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqTo}
import org.mockito.Matchers._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class GroupServiceSpec extends UnitSpec with MockitoSugar with SCRSMocks {

  implicit val hc = HeaderCarrier()

  class Setup {

    val service = new GroupService {
      override val compRegConnector: CompanyRegistrationConnector = mockCompanyRegistrationConnector
      override val keystoreConnector: KeystoreConnector = mockKeystoreConnector
      override val incorpInfoConnector: IncorpInfoConnector = mockIncorpInfoConnector
    }
    reset(mockCompanyRegistrationConnector)
  }

  "groupsUserSkippedPage" should {
    "redirect to post sign in if user url hopped (name page - no groups block)" in new Setup {
      val res = service.groupsUserSkippedPage(None, GroupPageEnum.shareholderNamePage)(groups => Results.Ok("foo"))
      status(res) shouldBe 303
      redirectLocation(res) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn().url)
    }
    "redirect to post sign in if user url hopped (address page no name block)" in new Setup {
      val res = service.groupsUserSkippedPage(Some(Groups(true, None, None, None)), GroupPageEnum.shareholderAddressPage)(groups => Results.Ok("foo"))
      status(res) shouldBe 303
      redirectLocation(res) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn().url)
    }
    "redirect to post sign in if user url hopped (utr page no name or address block)" in new Setup {
      val res = service.groupsUserSkippedPage(Some(Groups(true, None, None, None)), GroupPageEnum.utrPage)(groups => Results.Ok("foo"))
      status(res) shouldBe 303
      redirectLocation(res) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn().url)
    }
    "redirect to post sign in if user url hopped (utr page a name but no address block)" in new Setup {
      val res = service.groupsUserSkippedPage(Some(Groups(true, Some(GroupCompanyName("Company Name", "type")), None, None)), GroupPageEnum.utrPage)(groups => Results.Ok("foo"))
      status(res) shouldBe 303
      redirectLocation(res) shouldBe Some(controllers.reg.routes.SignInOutController.postSignIn().url)
    }

    "show the page in the passed in function if group block there and has full block on utr page" in new Setup {
      val res = service.groupsUserSkippedPage(Some(Groups(true,
        Some(GroupCompanyName("Company Name", "type")),
        Some(GroupsAddressAndType("type", NewAddress("l1", "l2", None, None, None, None, None))),
        None)),
        GroupPageEnum.utrPage)(groups => Results.Ok("foo"))
      status(res) shouldBe 200

    }
  }

  "updateGroupRelief" should {
    val defaultGroups = Groups(false,None,None,None)
    val groups = Groups(true,
      nameOfCompany = Some(GroupCompanyName("foo","Other")),
      addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))
    "return default group block, for group relief == false and a block already exists" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(),any())(any())).thenReturn(Future.successful(defaultGroups))
      val res = await(service.updateGroupRelief(false,Some(groups),"foo"))
      res shouldBe defaultGroups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
    "return default block for group relief == false and block does not exist" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(),any())(any())).thenReturn(Future.successful(defaultGroups))
      val res = await(service.updateGroupRelief(false,None,"foo"))
      res shouldBe defaultGroups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
    "return default block and update with default block with group relief set to true if true passed in" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(),any())(any())).thenReturn(Future.successful(defaultGroups.copy(groupRelief = true)))
      val res = await(service.updateGroupRelief(true,None,"foo"))
      res shouldBe defaultGroups.copy(groupRelief = true)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
    "return block passed in if group relief set to true plus update company reg" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(),any())(any())).thenReturn(Future.successful(groups))
      val res = await(service.updateGroupRelief(true,Some(groups),"foo"))
      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
  }

  "updateGroupName" should {

    val groups = Groups(true,nameOfCompany = Some(GroupCompanyName("foo", "CohoEntered")),None,None)
    val completeGroupsBlock = Groups(true,nameOfCompany = Some(GroupCompanyName("old name", "CohoEntered")),
      addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))

    "return a group block with the updated company name" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(groups))
      val res = await(service.updateGroupName(GroupCompanyName("foo","CohoEntered"), completeGroupsBlock, "reg1"))
      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "if a different name to that stored in CR is used, return a group block with the updated company name but null the address and utr fields" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(groups))
      val res = await(service.updateGroupName(GroupCompanyName("foo","CohoEntered"), groups, "reg1"))
      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
  }

  "updateGroupAddress" should {

    val groups = Groups(true, nameOfCompany = Some(GroupCompanyName("foo", "CohoEntered")),
      Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      groupUTR = None)
    val completeGroupsBlock = Groups(true, nameOfCompany = Some(GroupCompanyName("old name", "CohoEntered")),
      addressAndType = Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))

    "return a group block with the updated group address" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(completeGroupsBlock))
      val res = await(service.updateGroupAddress(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))), completeGroupsBlock, "reg1"))
      res shouldBe completeGroupsBlock
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
    "return a group block with the updated group address but remove the UTR block if the address has changed" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(groups))
      val res = await(service.updateGroupAddress(GroupsAddressAndType("ALF", NewAddress("Different l1", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))), completeGroupsBlock, "reg1"))
      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
  }

  "updateGroupUtr" should {
    val groups = Groups(true,
      nameOfCompany = Some(GroupCompanyName("foo","Other")),
      addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
      groupUTR = Some(GroupUTR(Some("1234567890"))))
    "updates existing block with different UTR value" in new Setup {
      val updatedGroups = groups.copy(groupUTR = Some(GroupUTR(Some("1ABC"))))
      when(mockCompanyRegistrationConnector.updateGroups(eqTo("bar"),eqTo(updatedGroups))(any())).thenReturn(Future.successful(updatedGroups))
      val res = await(service.updateGroupUtr(GroupUTR(Some("1ABC")),groups,"bar"))
      res shouldBe updatedGroups

      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
    "update with no utr" in new Setup {
      val updatedGroups = groups.copy(groupUTR = Some(GroupUTR(None)))
      when(mockCompanyRegistrationConnector.updateGroups(eqTo("bar"),eqTo(updatedGroups))(any())).thenReturn(Future.successful(updatedGroups))
      val res = await(service.updateGroupUtr(GroupUTR(None),groups,"bar"))
      res shouldBe updatedGroups

      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(),any())(any())
    }
  }

  "potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList" should{
    val listOfShareholders = List(
      Shareholder("big company",Some(75.0),Some(75.0),Some(75.0),CHROAddress("11","Add L1",Some("Add L2"),"London","United Kingdom",None,Some("ZZ1 1ZZ"),None))
    )
    "return a list of shareholders" in new Setup{
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholders)))

      val res = await(service.potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList("1"))
      res shouldBe listOfShareholders
    }

    "return an empty list if there arent any shareholders" in new Setup{
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))

      when(mockCompanyRegistrationConnector.deleteGroups(any())(any())).thenReturn(Future.successful(true))

      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(List.empty)))

      val res = await(service.potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList("1"))
      res shouldBe List.empty
    }
  }

  "retrieveGroups" should {
    "gets groups" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Some(Groups(true,None,None,None)))
      val res = await(service.retrieveGroups("foo"))
      res shouldBe Some(Groups(true,None,None,None))
    }
  }
  "hasDataChangedIfSoDropGroups" should {
    val listOfShareholders = List(
      Shareholder("big company",Some(75.0),Some(75.0),Some(75.0),CHROAddress("11","Add L1",Some("Add L2"),"London","United Kingdom",None,Some("ZZ1 1ZZ"),None)),
      Shareholder("big company 1",Some(74.3),Some(75.0),Some(75.0),CHROAddress("11 FOO","Add L1 1",Some("Add L2 2"),"London 1","United Kingdom 1",None,Some("ZZ1 1ZZ 1"),None))
    )
    "drop groups if groups block does not exist because name is not in list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("foo")))
      val res = await(service.hasDataChangedIfSoDropGroups(listOfShareholders,"foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }
    "drop groups block if user has groups block, name is NOT other AND name not in list of shareholders" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Future.successful(Some(
        Groups(true,Some(GroupCompanyName("foo","sausages")),None,None))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("wizzle", "bar")))
      when(mockCompanyRegistrationConnector.deleteGroups(any())(any())).thenReturn(Future.successful(true))
      val res = await(service.hasDataChangedIfSoDropGroups(listOfShareholders,"foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(1)).deleteGroups(any())(any())
    }
    "do not drop groups block if name is other but name is NOT in the list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Future.successful(Some(
        Groups(true,Some(GroupCompanyName("walls","Other")),None,None))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("foo")))

      val res = await(service.hasDataChangedIfSoDropGroups(listOfShareholders,"foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }
    "do not drop groups block if name is other and name is in the list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Future.successful(Some(
        Groups(true,Some(GroupCompanyName("foo","Other")),None,None))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("foo")))
      val res = await(service.hasDataChangedIfSoDropGroups(listOfShareholders,"foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }
    "do not drop groups block if groups block exists but does not have name block" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any())).thenReturn(Future.successful(Some(
        Groups(true,None,None,None))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("foo")))
      val res = await(service.hasDataChangedIfSoDropGroups(listOfShareholders,"bar"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }
  }

  "returnAddressOfShareholder" should {
    val listOfShareholders = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("big company 2", Some(75.0), Some(75.0), None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 3", Some(75.0), None, Some(75.0), CHROAddress("11 FOO3", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 4", None, None, None, CHROAddress("11 FOO 4", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None))
    )
    "return the address from the list of shareholders for the passed in Shareholder Company Name" in new Setup {
      val companyName = "big company 3"
      val expectedResult = CHROAddress("11 FOO3", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)
      val res = await(service.returnAddressOfShareholder(listOfShareholders,companyName))
      res shouldBe Some(expectedResult)
    }

    "return a NONE from the list of shareholders when the passed in Company Name does not exist" in new Setup {
      val companyName = "big company 7"
      val expectedResult = None
      val res = await(service.returnAddressOfShareholder(listOfShareholders,companyName))
      res shouldBe expectedResult
    }
  }

  "returnListOfShareholdersFromTxApi" should {
    val listOfShareholders = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("big company 2", Some(75.0), Some(75.0), None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 3", Some(75.0), None, Some(75.0), CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 4", None, None, None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None))
    )
    val expected = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    )
    "return a list of shareholders from coho and filter out shareholders without all 3 voting rights" in new Setup {
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholders))
      )
      val res = await(service.returnListOfShareholdersFromTxApi("foo"))
      res shouldBe Right(expected)
    }
    "return a list of shareholders from coho and filter out shareholders with voting right < 75" in new Setup {
      val listOfShareholdersLowerThan75 = List(
        Shareholder("big company", Some(74.9999999998), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 1", Some(74), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 2", Some(74.00), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 3", Some(74.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 4", Some(75.0), Some(73.0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 5", Some(75.01), Some(0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
      )
      val expectedFiltered = List(
        Shareholder("big company 4", Some(75.0), Some(73.0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
        Shareholder("big company 5", Some(75.01), Some(0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholdersLowerThan75))
      )
      val res = await(service.returnListOfShareholdersFromTxApi("foo"))
      res shouldBe Right(expectedFiltered)
    }
    "return an empty list if ii returns an empty list" in new Setup {
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(List.empty[Shareholder])))
      val res = await(service.returnListOfShareholdersFromTxApi("foo"))
      res shouldBe Right(List.empty[Shareholder])
    }
    "if II returns Left exception failed. this returns a Left exception" in new Setup {
      val ex = new Exception("")
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Left(ex)))
  await(service.returnListOfShareholdersFromTxApi("foo")).left.get shouldBe ex
    }
  }
  "returnListOfEligibleShareholdersFromCRDesValidationEndpoint" should {
    val shareholders = List(
      Shareholder("foo", Some(75.0), Some(73.0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("bar", Some(75.01), Some(0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    )
    "return a list of filtered names based on cr response" in new Setup {
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo")))
      val res = await(service.returnListOfEligibleShareholdersFromCRDesValidationEndpoint(shareholders))
      res shouldBe List("foo")
    }
    "return future failed if crconnector returns future failed" in new Setup {
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.failed(new Exception("foo")))
      intercept[Exception](await(service.returnListOfEligibleShareholdersFromCRDesValidationEndpoint(shareholders)))
    }
  }
  "checkGroupNameMatchAndPotentiallyDropOptionalBlocks" should {
    val shareholders = List(
      Shareholder("foo", Some(75.0), Some(73.0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("bar", Some(75.01), Some(0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    )
    "return Future list of string and groups for OTHER name, no drop occurs in cr even though name matches" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("wiZZ 123", "Other")), None, None), "123"))
      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(true, Some(GroupCompanyName("wiZZ 123", "Other")), None, None)
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }
    "return a future list removing duplicates if name not in list and IS other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo","foo","foo","bar","bar", "bar", "wiZZ 123")))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("foozbawl", "Other")), None, None), "123"))
      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(true, Some(GroupCompanyName("foozbawl", "Other")), None, None)
    }
    "return a future list removing duplicates if name IS in list and IS NOT other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo","foo","foo","bar","bar", "bar", "wiZZ 123")))

      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("foo", "BarType")), None, None), "123"))
      res._1 shouldBe List("bar", "wiZZ 123")
      res._2 shouldBe Groups(true, Some(GroupCompanyName("foo", "BarType")), None, None)
    }
    "return a future list removing duplicates if groups name is empty" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo","foo","foo","bar","bar", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(Groups(false, None, None, None)))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, None, None, None), "123"))
      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(false, None, None, None)
    }
    "return future list of string and group where name does not exist - a drop takes place of all other fields" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(Groups(false, None, None, None)))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, None, None, None), "123"))
      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(false, None, None, None)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())

    }
    "return future  filtered list of string and group where name is in list and is not Other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))

      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("wiZZ 123", "CohoEntered")), None, None), "123"))

      res._1 shouldBe List("foo", "bar")
      res._2 shouldBe Groups(true, Some(GroupCompanyName("wiZZ 123", "CohoEntered")), None, None)
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }
    "return future NON filtered list of string and group where name is NOT in the list and is NOT Other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(Groups(true, None, None, None)))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None), "123"))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(true, None, None, None)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
    "return empty list and  groups passed in if Left returned from returnListOfShareholdersFromTxApi and nothing updated because coho had a wobble" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo")))
      )
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Left(new Exception("foo uh oh"))))
      val res = await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None), "123"))
      res._1 shouldBe List.empty
      res._2 shouldBe Groups(true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None)

      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())

    }
    "return exception if confirmation references is not a success" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(
        Future.successful(ConfirmationReferencesErrorResponse))
      intercept[Exception](await(service.checkGroupNameMatchAndPotentiallyDropOptionalBlocks(
        Groups(true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None), "123")))
    }
  }

  "fetchTxID" should {
    "return future string if call to compRegConnector is successful" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))

      val res = await(service.fetchTxID("bar"))
      res shouldBe "foo"
    }
    "return exception if call to compRegConnector isnt successful" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesErrorResponse))

     intercept[Exception](await(service.fetchTxID("bar")))
    }
  }

  "returnAddressFromTxAPIBasedOnShareholderName" should {
    val listOfShareholders = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("big company 2", Some(75.0), Some(75.0), None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 3", Some(75.0), None, Some(75.0), CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 4", None, None, None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None))
    )
    val groups = Groups(true, Some(GroupCompanyName("big company", "CohoEntered")), None, None)
    "return address if returnListOfShareholdersFromTxApi returns right and name of company IS in list" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholders))
      )

      val res = await(service.returnAddressFromTxAPIBasedOnShareholderName(groups,"foo"))
      res.right.get shouldBe Some(CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))

    }
    "return address if 2 identical records exist that match" in new Setup {
      val identical = listOfShareholders ++ List(Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1 IDENTICAL NAME", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)))

      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(identical))
      )

      val res = await(service.returnAddressFromTxAPIBasedOnShareholderName(groups,"foo"))
      res.right.get shouldBe Some(CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    }
    "return None if returnListOfShareholdersFromTxApi returns right and name of company IS NOT in list" in new Setup {
      val groupsDoesntMatch = Groups(true, Some(GroupCompanyName("big company ", "CohoEntered")), None, None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholders))
      )

      val res = await(service.returnAddressFromTxAPIBasedOnShareholderName(groupsDoesntMatch,"foo"))
      res.right.get shouldBe None
    }
    "return None if returnListOfShareholdersFromTxApi returns right and list is empty" in new Setup {

      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(List.empty))
      )

      val res = await(service.returnAddressFromTxAPIBasedOnShareholderName(groups,"foo"))
      res.right.get shouldBe None
    }
    "return left exception if returnListOfShareholdersFromTxApi returns exception" in new Setup {
      val ex = new Exception("foo")
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Left(ex))
      )
      val res = await(service.returnAddressFromTxAPIBasedOnShareholderName(groups,"foo"))
      res.left.get shouldBe ex
    }
    "return exception if fetch confirmation references returns exception" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesErrorResponse)
      )
      intercept[Exception](await(service.returnAddressFromTxAPIBasedOnShareholderName(groups,"foo")))
    }
  }
  "returnAddressFromTXAPIValidateAndMatchWithCR" should {
    val listOfShareholder = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)))
    "return NONE NONE when call to txapi returns left (meaning coho had a blip)" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Left(new Exception("")))
      )

      val res = await(service.returnAddressFromTXAPIValidateAndMatchWithCR(Groups(false,None,None,None),"foo"))
      res shouldBe (None,None)
    }
    "return address and Some(true) when address is valid and matches one in groups block" in new Setup {
      val address = NewAddress("1","2",Some("3"),Some("4"),Some("5"),Some("6"), None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(Some(address)))
      val res = await(service.returnAddressFromTXAPIValidateAndMatchWithCR(
        Groups(
          true,
          Some(GroupCompanyName("big company", "CohoEntered")),
          Some(GroupsAddressAndType("CohoEntered", address)),
          None
        ), ""))
      res shouldBe (Some(address), Some(true))


    }
    "return address and Some(false) when address is valid but does not match the one in the groups block" in new Setup {
      val address = NewAddress("1","2",Some("3"),Some("4"),Some("5"),Some("6"), None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(Some(address)))
      val res = await(service.returnAddressFromTXAPIValidateAndMatchWithCR(
        Groups(
          true,
          Some(GroupCompanyName("big company", "CohoEntered")),
          Some(GroupsAddressAndType("CohoEntered", address.copy(addressLine1 = "walls sausages"))),
          None
        ), ""))
      res shouldBe (Some(address), Some(false))
    }
    "return no address and Some(false) when address invalid" in new Setup {
      val address = NewAddress("1","2",Some("3"),Some("4"),Some("5"),Some("6"), None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(None))
      val res = await(service.returnAddressFromTXAPIValidateAndMatchWithCR(
        Groups(
          true,
          Some(GroupCompanyName("big company NOT MATCH", "CohoEntered")),
          Some(GroupsAddressAndType("CohoEntered", address)),
          None
        ), ""))
      res shouldBe (None, Some(false))
    }
  }
  "returnMapOfAddresses" should {
    val address = NewAddress("1","2",Some("3"),Some("4"),Some("5"),Some("6"), None)
    val groupsAddressAndTypeALF = Some(GroupsAddressAndType("ALF", address))
    val groupsAddressAndTypeCoho = Some(GroupsAddressAndType("CohoEntered", address))
    """ return of Map("ALF" -> addr.mkString) - address matched, IS ALF""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(true),groupsAddressAndTypeALF, None)
      res shouldBe Map("ALF" -> address.mkString)
    }
    """ return of Map("ALF" -> addr.mkString, "TxAPI" -> optNewAddressFromTxApi.get.mkString) - address not matched, IS ALF TX Address exists""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(false),groupsAddressAndTypeALF, Some(address))
      res shouldBe Map("ALF" -> address.mkString, "TxAPI" -> address.mkString)
    }
    """ return of Map("ALF" -> addr.mkString)  - address not matched, IS ALF TX Address does not exist""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(false),groupsAddressAndTypeALF, None)
      res shouldBe Map("ALF" -> address.mkString)
    }
    """ return of  Map(addressType -> addr.mkString) - address matched, not ALF""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(true), groupsAddressAndTypeCoho, None)
      res shouldBe Map("CohoEntered" -> address.mkString)
    }
    """ return of Map("TxAPI" -> optNewAddressFromTxApi.get.mkString) - No address in CR, tx address exists""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(false), None, Some(address.copy(addressLine1 = "foo")))
      res shouldBe Map("TxAPI" -> address.copy(addressLine1 = "foo").mkString)
    }
    """return Map.empty no address in cr / tx api had blip and returned nothing None""" in new Setup {
      val res = service.returnMapOfAddresses(
        None, None, None)
      res shouldBe Map.empty
    }
    """return Map.empty no address in cr / tx api had blip and returned nothing Some(false)""" in new Setup {
      val res = service.returnMapOfAddresses(
        Some(false), None, None)
      res shouldBe Map.empty
    }
  }
  "returnMapOfAddressesMatchDropAndReturnUpdatedGroups" should {
    val address = NewAddress("11 Add L1","Add L2",None,Some("London"),Some("ZZ1 1ZZ"),Some("United Kingdom"), None)
    val groupsWithSameAddress = Groups(
      true,
      Some(GroupCompanyName("big company", "CohoEntered")),
      Some(GroupsAddressAndType("CohoEntered", address)),
      None
    )
    val groups = Groups(
      true,
      Some(GroupCompanyName("big company", "CohoEntered")),
      Some(GroupsAddressAndType("CohoEntered", address.copy(addressLine2 = "2"))),
      None
    )
    val listOfShareholder = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)))
    "return map of string string and updated Groups because addresses dont match drop of block occurs" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(Some(address)))
      when(mockCompanyRegistrationConnector.updateGroups(any(),any())(any()))
        .thenReturn(Future.successful(Groups(true,None,None,None)))

      val res = await(service.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(groups,"foo"))
      res._1 shouldBe Map("TxAPI" -> address.mkString)
      res._2 shouldBe Groups(true,None,None,None)
      verify(mockCompanyRegistrationConnector,times(1)).updateGroups(any(),any())(any())

    }
    "return map of string string and not update groups because address matches, no drop occurs" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(Some(address)))

      val res = await(service.returnMapOfAddressesMatchDropAndReturnUpdatedGroups(groupsWithSameAddress,"foo"))
      res._1 shouldBe Map("CohoEntered" -> address.mkString)
      res._2 shouldBe groupsWithSameAddress
      verify(mockCompanyRegistrationConnector,times(0)).updateGroups(any(),any())(any())
    }

  }
  "saveTxShareHolderAddress" should {
    val address = NewAddress("11 Add L1","Add L2",None,Some("London"),Some("ZZ1 1ZZ"),Some("United Kingdom"), None)
    val listOfShareholder = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)))
    "return a left exception if txapi has a blip and connector call returns left" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Left(new Exception("foo"))))
      val res = await(service.saveTxShareHolderAddress(Groups(true,None,None,None),""))
      res.left.get.getMessage shouldBe "Address validation on CR returned nothing back on submit"
    }
    "return a  right updated group block, and update groups if returns address after validation" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo",None,None,""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(listOfShareholder))
      )
      when(mockCompanyRegistrationConnector.checkValidShareHolderAddressFromCoho(any(),any())(any()))
        .thenReturn(Future.successful(Some(address)))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any())).thenReturn(Future.successful(Groups(false,None,None,None)))

      val res = await(service.saveTxShareHolderAddress(Groups(true,Some(GroupCompanyName("big company","CohoEntered")),None,None),""))
      res.right.get shouldBe Groups(false,None,None,None)
    }
  }
}