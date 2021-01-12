/*
 * Copyright 2021 HM Revenue & Customs
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

import mocks.SCRSMocks
import models._
import models.connectors.ConfirmationReferences
import org.mockito.Matchers.{eq => eqTo, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GroupServiceSpec extends UnitSpec with MockitoSugar with SCRSMocks {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  class Setup {
    val service: GroupService = new GroupService(
      mockKeystoreConnector,
      mockCompanyRegistrationConnector,
      mockIncorpInfoConnector
    )

    reset(mockCompanyRegistrationConnector)
  }

  "retrieveGroups" should {
    "gets groups" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Some(Groups(groupRelief = true, None, None, None)))

      val res: Option[Groups] = await(service.retrieveGroups("foo"))
      res shouldBe Some(Groups(groupRelief = true, None, None, None))
    }
  }

  "updateGroupRelief" should {
    val defaultGroups = Groups(groupRelief = false, None, None, None)
    val groups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("foo", "Other")),
      Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      Some(GroupUTR(Some("1234567890")))
    )

    "return empty groups with relief = false when choosing false with prepop data" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Some(groups))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(defaultGroups))

      val res: Groups = await(service.updateGroupRelief(groupRelief = false, "foo"))
      res shouldBe defaultGroups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return empty groups with relief = false when choosing false with no prepop data" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(None)
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(defaultGroups))

      val res: Groups = await(service.updateGroupRelief(groupRelief = false, "foo"))
      res shouldBe defaultGroups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return empty groups with relief = truew when choosing true with no prepop data" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(None)
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(defaultGroups.copy(groupRelief = true)))

      val res: Groups = await(service.updateGroupRelief(groupRelief = true, "foo"))
      res shouldBe defaultGroups.copy(groupRelief = true)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return groups with relief = true when choosing true with prepop data" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Some(groups))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(groups))

      val res: Groups = await(service.updateGroupRelief(groupRelief = true, "foo"))
      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
  }

  "updateGroupAddress" should {
    val groups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("foo", "CohoEntered")),
      Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      None
    )
    val completeGroupsBlock = Groups(
      groupRelief = true,
      Some(GroupCompanyName("old name", "CohoEntered")),
      Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      Some(GroupUTR(Some("1234567890")))
    )

    "return a group block with the updated group address" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(completeGroupsBlock))
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Some(completeGroupsBlock))

      val res: Groups = await(service.updateGroupAddress(
        GroupsAddressAndType(
          "ALF",
          NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))
        ),
        registrationId = "reg1"
      ))

      res shouldBe completeGroupsBlock
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return a group block with the updated group address but remove the UTR block if the address has changed" in new Setup {
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(groups))
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Some(completeGroupsBlock))

      val res: Groups = await(service.updateGroupAddress(
        GroupsAddressAndType(
          "ALF",
          NewAddress("Different l1", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A"))
        ),
        registrationId = "reg1"
      ))

      res shouldBe groups
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
  }

  "updateGroupUtr" should {
    val groups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("foo", "Other")),
      Some(GroupsAddressAndType("ALF", NewAddress("1 abc", "2 abc", Some("3 abc"), Some("4 abc"), Some("ZZ1 1ZZ"), Some("country A")))),
      Some(GroupUTR(Some("1234567890")))
    )

    "updates existing block with different UTR value" in new Setup {
      val updatedGroups: Groups = groups.copy(groupUTR = Some(GroupUTR(Some("1ABC"))))
      when(mockCompanyRegistrationConnector.updateGroups(eqTo("bar"), eqTo(updatedGroups))(any()))
        .thenReturn(Future.successful(updatedGroups))

      val res: Groups = await(service.updateGroupUtr(GroupUTR(Some("1ABC")), groups, "bar"))
      res shouldBe updatedGroups

      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "update with no utr" in new Setup {
      val updatedGroups: Groups = groups.copy(groupUTR = Some(GroupUTR(None)))
      when(mockCompanyRegistrationConnector.updateGroups(eqTo("bar"), eqTo(updatedGroups))(any()))
        .thenReturn(Future.successful(updatedGroups))

      val res: Groups = await(service.updateGroupUtr(GroupUTR(None), groups, "bar"))
      res shouldBe updatedGroups

      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }
  }

  "fetchTxID" should {
    "return future string if call to compRegConnector is successful" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))

      val res: String = await(service.fetchTxID("bar"))
      res shouldBe "foo"
    }
    "return exception if call to compRegConnector isnt successful" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesErrorResponse))

      intercept[Exception](await(service.fetchTxID("bar")))
    }
  }

  "returnListOfShareholders" should {
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
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholders)))

      val res: Either[Exception, List[Shareholder]] = await(service.returnListOfShareholders("foo"))
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
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholdersLowerThan75)))

      val res: Either[Exception, List[Shareholder]] = await(service.returnListOfShareholders("foo"))
      res shouldBe Right(expectedFiltered)
    }

    "return an empty list if ii returns an empty list" in new Setup {
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any())).thenReturn(
        Future.successful(Right(List.empty[Shareholder])))

      val res: Either[Exception, List[Shareholder]] = await(service.returnListOfShareholders("foo"))
      res shouldBe Right(List.empty[Shareholder])
    }

    "if II returns Left exception failed. this returns a Left exception" in new Setup {
      val ex = new InternalServerException("")
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Left(ex)))

      await(service.returnListOfShareholders("foo")).left.get shouldBe ex
    }
  }

  "createAddressMap" should {
    val address = NewAddress("1", "2", Some("3"), Some("4"), Some("5"), Some("6"), None)
    val otherAddress = NewAddress("10", "20", Some("30"), Some("40"), Some("50"), Some("60"), None)
    val groupsAddressAndTypeALF = Some(GroupsAddressAndType("ALF", address))
    val groupsAddressAndTypeCoho = Some(GroupsAddressAndType("CohoEntered", address))

    """ return of Map("ALF" -> address) - address matched, IS ALF""" in new Setup {
      val res: Map[String, String] = service.createAddressMap(groupsAddressAndTypeALF, address)
      res shouldBe Map("ALF" -> address.mkString)
    }

    """ return of Map("ALF" -> address, "TxAPI" -> otherAddress) - address not matched, IS ALF TX Address exists""" in new Setup {
      val res: Map[String, String] = service.createAddressMap(groupsAddressAndTypeALF, otherAddress)
      res shouldBe Map("ALF" -> address.mkString, "TxAPI" -> otherAddress.mkString)
    }

    """ return of  Map(addressType -> address) - address matched, not ALF""" in new Setup {
      val res: Map[String, String] = service.createAddressMap(groupsAddressAndTypeCoho, address)
      res shouldBe Map("CohoEntered" -> address.mkString)
    }

    """ return of Map("TxAPI" -> address) - No address in CR, tx address exists""" in new Setup {
      val res: Map[String, String] = service.createAddressMap(None, address)
      res shouldBe Map("TxAPI" -> address.mkString)
    }
  }

  "returnAddressFromTxAPI" should {
    val listOfShareholders = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("big company 2", Some(75.0), Some(75.0), None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 3", Some(75.0), None, Some(75.0), CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None)),
      Shareholder("big company 4", None, None, None, CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None))
    )
    val groupCompanyName = GroupCompanyName("big company", "CohoEntered")

    "return address if returnListOfShareholdersFromTxApi returns right and name of company IS in list" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholders)))

      val res: Either[Exception, Option[CHROAddress]] = await(service.returnAddressFromTxAPI(groupCompanyName, "foo"))
      res.right.get shouldBe Some(CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))

    }

    "return address if 2 identical records exist that match" in new Setup {
      val identical: List[Shareholder] = listOfShareholders ++ List(
        Shareholder(
          "big company",
          Some(75.0),
          Some(75.0),
          Some(75.0),
          CHROAddress("11", "Add L1 IDENTICAL NAME", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)
        )
      )

      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(identical)))

      val res: Either[Exception, Option[CHROAddress]] = await(service.returnAddressFromTxAPI(groupCompanyName, "foo"))
      res.right.get shouldBe Some(CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    }

    "return None if returnListOfShareholdersFromTxApi returns right and name of company IS NOT in list" in new Setup {
      val groupCompanyNameDoesntMatch = GroupCompanyName("big company ", "CohoEntered")
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholders)))

      val res: Either[Exception, Option[CHROAddress]] = await(service.returnAddressFromTxAPI(groupCompanyNameDoesntMatch, "foo"))
      res.right.get shouldBe None
    }

    "return None if returnListOfShareholdersFromTxApi returns right and list is empty" in new Setup {

      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(List.empty)))

      val res: Either[Exception, Option[CHROAddress]] = await(service.returnAddressFromTxAPI(groupCompanyName, "foo"))
      res.right.get shouldBe None
    }

    "return left exception if returnListOfShareholdersFromTxApi returns exception" in new Setup {
      val ex = new Exception("foo")
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Left(ex)))

      val res: Either[Exception, Option[CHROAddress]] = await(service.returnAddressFromTxAPI(groupCompanyName, "foo"))
      res.left.get shouldBe ex
    }

    "return exception if fetch confirmation references returns exception" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesErrorResponse))

      intercept[Exception](await(service.returnAddressFromTxAPI(groupCompanyName, "foo")))
    }
  }

  "retreiveValidatedTxApiAddress" should {
    val listOfShareholder = List(
      Shareholder("big company",
        Some(75.0),
        Some(75.0),
        Some(75.0),
        CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)
      )
    )

    "return exception if there is no groupCompanyName" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Left(new Exception(""))))

      val res: InternalServerException = intercept[InternalServerException](service.retreiveValidatedTxApiAddress(Groups(groupRelief = false, None, None, None), "foo"))
      res.message shouldBe "[GroupService] [retreiveTxApiAddress] attempted to find txApi address without prerequesite data"
    }

    "return address when address is valid" in new Setup {
      val address = NewAddress("1", "2", Some("3"), Some("4"), Some("5"), Some("6"), None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholder)))
      when(mockCompanyRegistrationConnector.validateRegisteredOfficeAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(address)))

      val res: Option[NewAddress] = await(service.retreiveValidatedTxApiAddress(
        Groups(
          groupRelief = true,
          Some(GroupCompanyName("big company", "CohoEntered")),
          Some(GroupsAddressAndType("CohoEntered", address)),
          None
        ), ""))
      res shouldBe Some(address)
    }

    "return no address when address invalid" in new Setup {
      val address = NewAddress("1", "2", Some("3"), Some("4"), Some("5"), Some("6"), None)
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholder)))
      when(mockCompanyRegistrationConnector.validateRegisteredOfficeAddress(any(), any())(any()))
        .thenReturn(Future.successful(None))

      val res: Option[NewAddress] = await(service.retreiveValidatedTxApiAddress(
        Groups(
          groupRelief = true,
          Some(GroupCompanyName("big company NOT MATCH", "CohoEntered")),
          Some(GroupsAddressAndType("CohoEntered", address)),
          None
        ), ""))
      res shouldBe None
    }
  }

  "dropOldFields" should {
    val address = NewAddress("11 Add L1", "Add L2", None, Some("London"), Some("ZZ1 1ZZ"), Some("United Kingdom"), None)
    val groupsWithSameAddress = Groups(
      groupRelief = true,
      Some(GroupCompanyName("big company", "CohoEntered")),
      Some(GroupsAddressAndType("CohoEntered", address)),
      None
    )
    val groups = Groups(
      groupRelief = true,
      Some(GroupCompanyName("big company", "CohoEntered")),
      Some(GroupsAddressAndType("CohoEntered", address.copy(addressLine2 = "2"))),
      None
    )

    "return groups after dropping old address and companyUtr if the address does not match" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(Groups(groupRelief = true, None, None, None)))

      val res: Groups = await(service.dropOldFields(groups, address, "foo"))
      res shouldBe Groups(groupRelief = true, None, None, None)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())

    }
    "return unchanged groups if the address matches" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockCompanyRegistrationConnector.validateRegisteredOfficeAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(address)))

      val res: Groups = await(service.dropOldFields(groupsWithSameAddress, address, "foo"))
      res shouldBe groupsWithSameAddress
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }

  }

  "saveTxShareHolderAddress" should {
    val address = NewAddress("11 Add L1", "Add L2", None, Some("London"), Some("ZZ1 1ZZ"), Some("United Kingdom"), None)
    val listOfShareholders = List(
      Shareholder(
        "big company",
        Some(75.0),
        Some(75.0),
        Some(75.0),
        CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)
      )
    )

    "return a left exception if txapi has a blip and connector call returns left" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Left(new Exception("foo"))))

      val res: Either[Exception, Groups] = await(service.saveTxShareHolderAddress(
        Groups(groupRelief = true, Some(GroupCompanyName("big company", "CohoEntered")), None, None),
        registrationID = ""
      ))
      res.left.get.getMessage shouldBe "[GroupService] [saveTxShareHolderAddress] Attempted to save TxApiAddress but none was found"
    }

    "return a  right updated group block, and update groups if returns address after validation" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(listOfShareholders)))
      when(mockCompanyRegistrationConnector.validateRegisteredOfficeAddress(any(), any())(any()))
        .thenReturn(Future.successful(Some(address)))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(Groups(groupRelief = false, None, None, None)))

      val res: Either[Exception, Groups] = await(service.saveTxShareHolderAddress(
        Groups(groupRelief = true, Some(GroupCompanyName("big company", "CohoEntered")), None, None),
        registrationID = ""
      ))
      res.right.get shouldBe Groups(groupRelief = false, None, None, None)
    }
  }

  "returnValidShareholdersAndUpdateGroups" should {
    val shareholders = List(
      Shareholder("foo", Some(75.0), Some(73.0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("bar", Some(75.01), Some(0), Some(7.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None))
    )

    "return Future list of string and groups for OTHER name, no drop occurs in cr even though name matches" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123", "Other")), None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123", "Other")), None, None)
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }

    "return a future list removing duplicates if name not in list and IS other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "foo", "foo", "bar", "bar", "bar", "wiZZ 123")))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("foozbawl", "Other")), None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = true, Some(GroupCompanyName("foozbawl", "Other")), None, None)
    }

    "return a future list removing duplicates if name IS in list and IS NOT other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "foo", "foo", "bar", "bar", "bar", "wiZZ 123")))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("foo", "BarType")), None, None),
        "123"
      ))

      res._1 shouldBe List("bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = true, Some(GroupCompanyName("foo", "BarType")), None, None)
    }

    "return a future list removing duplicates if groups name is empty" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo", "foo", "foo", "bar", "bar", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(Groups(groupRelief = false, None, None, None)))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, None, None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = false, None, None, None)
    }

    "return future list of string and group where name does not exist - a drop takes place of all other fields" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(Groups(groupRelief = false, None, None, None)))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, None, None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = false, None, None, None)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return future  filtered list of string and group where name is in list and is not Other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123", "CohoEntered")), None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar")
      res._2 shouldBe Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123", "CohoEntered")), None, None)
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }

    "return future NON filtered list of string and group where name is NOT in the list and is NOT Other" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Right(shareholders)))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(eqTo(List("foo", "bar")))(any()))
        .thenReturn(Future.successful(List("foo", "bar", "wiZZ 123")))
      when(mockCompanyRegistrationConnector.updateGroups(any(), any())(any()))
        .thenReturn(Future.successful(Groups(groupRelief = true, None, None, None)))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None),
        "123"
      ))

      res._1 shouldBe List("foo", "bar", "wiZZ 123")
      res._2 shouldBe Groups(groupRelief = true, None, None, None)
      verify(mockCompanyRegistrationConnector, times(1)).updateGroups(any(), any())(any())
    }

    "return empty list and  groups passed in if Left returned from returnListOfShareholdersFromTxApi and nothing updated because coho had a wobble" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesSuccessResponse(ConfirmationReferences("footxID", None, None, "foo"))))
      when(mockIncorpInfoConnector.returnListOfShareholdersFromTxApi(any())(any()))
        .thenReturn(Future.successful(Left(new Exception("foo uh oh"))))

      val res: (List[String], Groups) = await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None),
        "123"
      ))

      res._1 shouldBe List.empty
      res._2 shouldBe Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None)
      verify(mockCompanyRegistrationConnector, times(0)).updateGroups(any(), any())(any())
    }

    "return exception if confirmation references is not a success" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any()))
        .thenReturn(Future.successful(ConfirmationReferencesErrorResponse))

      intercept[Exception](await(service.returnValidShareholdersAndUpdateGroups(
        Groups(groupRelief = true, Some(GroupCompanyName("wiZZ 123 1", "CohoEntered")), None, None),
        "123"
      )))
    }
  }

  "potentiallyDropGroupsBasedOnReturnFromTXApiAndReturnList" should {
    val listOfShareholders = List(
      Shareholder("big company", Some(75.0), Some(75.0), Some(75.0), CHROAddress("11", "Add L1", Some("Add L2"), "London", "United Kingdom", None, Some("ZZ1 1ZZ"), None)),
      Shareholder("big company 1", Some(74.3), Some(75.0), Some(75.0), CHROAddress("11 FOO", "Add L1 1", Some("Add L2 2"), "London 1", "United Kingdom 1", None, Some("ZZ1 1ZZ 1"), None))
    )

    "return an empty list if there arent any shareholders" in new Setup {
      when(mockCompanyRegistrationConnector.fetchConfirmationReferences(any())(any())).thenReturn(Future.successful(
        ConfirmationReferencesSuccessResponse(ConfirmationReferences("foo", None, None, ""))))
      when(mockCompanyRegistrationConnector.deleteGroups(any())(any())).thenReturn(Future.successful(true))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(List.empty), "123"))
      res shouldBe List.empty
    }

    "drop groups if groups block does not exist because name is not in list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Future.successful(None))
      when(mockCompanyRegistrationConnector
        .shareholderListValidationEndpoint(any())(any())).thenReturn(Future.successful(List("foo")))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(listOfShareholders), "foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }

    "drop groups block if user has groups block, name is NOT other AND name not in list of shareholders" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Future.successful(Some(Groups(
          groupRelief = true,
          Some(GroupCompanyName("foo", "sausages")),
          None,
          None
        ))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("wizzle", "bar")))
      when(mockCompanyRegistrationConnector.deleteGroups(any())(any()))
        .thenReturn(Future.successful(true))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(listOfShareholders), "foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(1)).deleteGroups(any())(any())
    }

    "do not drop groups block if name is other but name is NOT in the list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Future.successful(Some(Groups(
          groupRelief = true,
          Some(GroupCompanyName("walls", "Other")),
          None,
          None
        ))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo")))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(listOfShareholders), "foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }

    "do not drop groups block if name is other and name is in the list" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Future.successful(Some(Groups(
          groupRelief = true,
          Some(GroupCompanyName("foo", "Other")),
          None,
          None
        ))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo")))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(listOfShareholders), "foo"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }

    "do not drop groups block if groups block exists but does not have name block" in new Setup {
      when(mockCompanyRegistrationConnector.getGroups(any())(any()))
        .thenReturn(Future.successful(Some(Groups(
          groupRelief = true,
          None,
          None,
          None
        ))))
      when(mockCompanyRegistrationConnector.shareholderListValidationEndpoint(any())(any()))
        .thenReturn(Future.successful(List("foo")))

      val res: List[Shareholder] = await(service.dropOldGroups(Right(listOfShareholders), "bar"))
      res shouldBe listOfShareholders
      verify(mockCompanyRegistrationConnector, times(0)).deleteGroups(any())(any())
    }
  }
}