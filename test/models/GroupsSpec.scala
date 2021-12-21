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

package models

import helpers.UnitSpec
import play.api.libs.json.Json


class GroupsSpec extends UnitSpec {

  "groups reads" should {
    "read in json successfully NOT validating it" in {
      val jsonToBeParsed = Json.parse("""{
                                  |   "groupRelief": true,
                                  |   "nameOfCompany": {
                                  |     "name": "foo",
                                  |     "nameType" : "Other"
                                  |   },
                                  |   "addressAndType" : {
                                  |     "addressType" : "ALF",
                                  |       "address" : {
                                  |         "line1": "1 abc",
                                  |         "line2" : "2 abc",
                                  |         "line3" : "3 abc",
                                  |         "line4" : "4 abc",
                                  |         "country" : "country A",
                                  |         "postcode" : "ZZ1 1ZZ"
                                  |     }
                                  |   },
                                  |   "groupUTR" : {
                                  |     "UTR" : "1234567890"
                                  |   }
                                  |}""".stripMargin)
      val expected = Groups(true,
        nameOfCompany = Some(GroupCompanyName("foo","Other")),
        addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))
      Json.fromJson[Groups](jsonToBeParsed).get shouldBe expected
    }
    "reads with minimal data" in {
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": true
                                        |}""".stripMargin)
      Json.fromJson[Groups](jsonToBeParsed).get shouldBe Groups(true,None,None,None)

    }
    "reads unsuccessfully where json is invalid" in {
      val jsonToBeParsed = Json.parse("""{
                                        |   "groupRelief": "foo"
                                        |}""".stripMargin)
      Json.fromJson[Groups](jsonToBeParsed).isError shouldBe true
    }
  }
  "groups writes" should {
    "write groups to json" in {
      val jsonToBeOutput = Json.parse("""{
                                        |   "groupRelief": true,
                                        |   "nameOfCompany": {
                                        |     "name": "foo",
                                        |     "nameType" : "Other"
                                        |   },
                                        |   "addressAndType" : {
                                        |     "addressType" : "ALF",
                                        |       "address" : {
                                        |         "line1": "1 abc",
                                        |         "line2" : "2 abc",
                                        |         "line3" : "3 abc",
                                        |         "line4" : "4 abc",
                                        |         "country" : "country A",
                                        |         "postcode" : "ZZ1 1ZZ"
                                        |     }
                                        |   },
                                        |   "groupUTR" : {
                                        |     "UTR" : "1234567890"
                                        |   }
                                        |}""".stripMargin)
      val groups = Groups(true,
        nameOfCompany = Some(GroupCompanyName("foo","Other")),
        addressAndType = Some(GroupsAddressAndType("ALF",NewAddress("1 abc", "2 abc",Some("3 abc"), Some("4 abc"),Some("ZZ1 1ZZ"),Some("country A")))),
        groupUTR = Some(GroupUTR(Some("1234567890"))))

      Json.toJson[Groups](groups) shouldBe jsonToBeOutput
    }
    "write minimal json" in {
      val groups  = Groups(true,None,None,None)
      val jsonOutput = Json.parse("""{
                                        |   "groupRelief": true
                                        |}""".stripMargin)
      Json.toJson[Groups](groups) shouldBe jsonOutput
    }
  }

  "shareholders reads" should {
    "read successfully for a list of shareholders returned from II where there are multiple shareholders (corp and non corp)" in {
      val listOfShareHoldersFromII = Json.parse("""[{
                                        |  "percentage_dividend_rights": 75,
                                        |  "percentage_voting_rights": 75.0,
                                        |  "percentage_capital_rights": 75,
                                        |  "corporate_name": "big company",
                                        |    "address": {
                                        |    "premises": "11",
                                        |    "address_line_1": "Add L1",
                                        |    "address_line_2": "Add L2",
                                        |    "locality": "London",
                                        |    "country": "United Kingdom",
                                        |    "postal_code": "ZZ1 1ZZ"
                                        |      }
                                        |    },{
                                        |  "percentage_dividend_rights": 75,
                                        |  "percentage_voting_rights": 74.3,
                                        |  "percentage_capital_rights": 75,
                                        |  "corporate_name": "big company 1",
                                        |    "address": {
                                        |    "premises": "11 FOO",
                                        |    "address_line_1": "Add L1 1",
                                        |    "address_line_2": "Add L2 2",
                                        |    "locality": "London 1",
                                        |    "country": "United Kingdom 1",
                                        |    "postal_code": "ZZ1 1ZZ 1"
                                        |      }
                                        |    },{
                                        |    "surname": "foo will never show",
                                        |    "forename" : "bar will never show",
                                        |    "percentage_dividend_rights": 75,
                                        |    "percentage_voting_rights": 75,
                                        |    "percentage_capital_rights": 75,
                                        |    "address": {
                                        |    "premises": "11",
                                        |    "address_line_1": "Add L1",
                                        |    "address_line_2": "Add L2",
                                        |    "locality": "London",
                                        |    "country": "United Kingdom",
                                        |    "postal_code": "ZZ1 1ZZ"
                                        |      }
                                        |    }
                                        |]""".stripMargin)

      val listOfShareholders = List(
        Shareholder("big company",Some(75.0),Some(75.0),Some(75.0),CHROAddress("11","Add L1",Some("Add L2"),"London","United Kingdom",None,Some("ZZ1 1ZZ"),None)),
        Shareholder("big company 1",Some(74.3),Some(75.0),Some(75.0),CHROAddress("11 FOO","Add L1 1",Some("Add L2 2"),"London 1","United Kingdom 1",None,Some("ZZ1 1ZZ 1"),None))
      )
      Json.fromJson[List[Shareholder]](listOfShareHoldersFromII).get shouldBe listOfShareholders

    }
    "read successfully with empty json" in {
      val listOfShareHoldersFromII = Json.parse("""[]""")
      Json.fromJson[List[Shareholder]](listOfShareHoldersFromII).get shouldBe List.empty
    }
    "read successfully where there is no corporate shareholders" in {
      val listOfShareHoldersFromII = Json.parse("""[{
                                                  |    "surname": "foo will never show",
                                                  |    "forename" : "bar will never show",
                                                  |    "percentage_dividend_rights": 75,
                                                  |    "percentage_voting_rights": 75,
                                                  |    "percentage_capital_rights": 75,
                                                  |    "address": {
                                                  |    "premises": "11",
                                                  |    "address_line_1": "Add L1",
                                                  |    "address_line_2": "Add L2",
                                                  |    "locality": "London",
                                                  |    "country": "United Kingdom",
                                                  |    "postal_code": "ZZ1 1ZZ"
                                                  |      }
                                                  |    }
                                                  |]""".stripMargin)

      Json.fromJson[List[Shareholder]](listOfShareHoldersFromII).get shouldBe List.empty
    }
  }
  "shareholder writes" should {
    "write shareholders to a list of string" in {
      val listOfShareholders = List(
        Shareholder("big company",Some(75.0),Some(75.0),Some(75.0),CHROAddress("11","Add L1",Some("Add L2"),"London","United Kingdom",None,Some("ZZ1 1ZZ"),None)),
        Shareholder("big company 1",Some(74.3),Some(75.0),Some(75.0),CHROAddress("11 FOO","Add L1 1",Some("Add L2 2"),"London 1","United Kingdom 1",None,Some("ZZ1 1ZZ 1"),None))
      )
      Json.toJson[List[Shareholder]](listOfShareholders) shouldBe Json.parse("""["big company","big company 1"]""")
    }
    "write an empty list of shareholders to an empty array" in {
      val listOfShareholders = List.empty[Shareholder]
      Json.toJson[List[Shareholder]](listOfShareholders) shouldBe Json.parse("""[]""")
    }
  }
}
