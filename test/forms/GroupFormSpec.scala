/*
 * Copyright 2022 HM Revenue & Customs
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

package forms

import helpers.UnitSpec
import models.{GroupCompanyName, GroupUTR}
import play.api.data.Form

class GroupFormSpec extends UnitSpec  {



  "formUTR" should {
    def fillForm(radioButtonValue: String, utrValue: Option[String]): Form[GroupUTR] = {
      GroupUtrForm.form.bind(Map("groupUtr" -> radioButtonValue, "utr" -> utrValue.getOrElse("")))
    }
    "have previously saved utr pre-popped in form 10 chars" in {
      val result = fillForm("utr", Some("99999999"))
      result.data("groupUtr") shouldBe "utr"
      result.get.UTR shouldBe Some("99999999")
    }

    "have previously saved utr pre-popped in form 1 char" in {
      val result = fillForm("utr", Some("1"))
      result.data("groupUtr") shouldBe "utr"
      result.get.UTR shouldBe Some("1")
    }

    "have previously saved no-utr answer" in {
      val result = fillForm("noutr", None)
      result.data("groupUtr") shouldBe "noutr"
    }

    "display an error when no answer is selected on the page" in {
      val result = fillForm("", Option(""))
      result.errors.head.message shouldBe "error.groupUtr.required"
    }

    "display an error when utr is blank" in {
      val result = fillForm("utr", None)
      result.data("groupUtr") shouldBe "utr"
      result.errors.head.message shouldBe "error.groupUtr.yesButNoUtr"
    }

    "display an error when utr is more than 10 chars" in {
      val result = fillForm("utr", Some("09876543211"))
      result.data("groupUtr") shouldBe "utr"
      result.errors.head.message shouldBe "error.groupUtr.utrMoreThan10Chars"
    }

    "display an error when utr contains letters" in {
      val result = fillForm("utr", Some("09876f"))
      result.data("groupUtr") shouldBe "utr"
      result.errors.head.message shouldBe "error.groupUtr.utrHasSymbols"
    }

    "display an error when utr contains special chars" in {
      val result = fillForm("utr", Some("1234567*!"))
      result.data("groupUtr") shouldBe "utr"
      result.errors.head.message shouldBe "error.groupUtr.utrHasSymbols"
    }

  }

  "formGroupRelief" should {
    def fillForm(radioButtonValue: String): Form[Boolean] = {
      GroupReliefForm.form.bind(Map("groupRelief" -> radioButtonValue))
    }
    "should return no errors for valid boolean" in {
      val result = fillForm("true")
      result.data("groupRelief") shouldBe "true"
      result.errors.isEmpty shouldBe true
    }
    "return error when empty" in {
      val result = fillForm("")
      result.data("groupRelief") shouldBe ""
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "error.groupRelief.required"

    }
    "return error for non boolean value" in {
      val result = fillForm("foo")
      result.data("groupRelief") shouldBe "foo"
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "error.groupRelief.required"
    }
  }
  "formGroupName" should {
    def fillForm(radioButtonValue: String, textBoxValue: Option[String]): Form[GroupCompanyName] = {
      GroupNameForm.form.bind(Map(
        "groupName" -> radioButtonValue,
        "otherName" -> textBoxValue.getOrElse(""))
      )
    }
    "return no errors when radio button submitted with valid value (not other), and field Other blank" in {
      val result = fillForm("company 1",None)
      result.data("groupName") shouldBe "company 1"
      result.data("otherName") shouldBe ""
      result.errors.isEmpty shouldBe true
    }
    "return no errors when radio button submitted with valid value of other with field other populated with valid name" in {
      val result = fillForm("company 1",Some("company 1"))
      result.data("groupName") shouldBe "company 1"
      result.data("otherName") shouldBe "company 1"
      result.errors.isEmpty shouldBe true
    }
    "return errors for empty form - nothing populated" in {
      val result = fillForm("",None)
      result.data("groupName") shouldBe ""
      result.data("otherName") shouldBe ""
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "error.groupName.required"
    }
    "return errors for other radio button selected but empty text box" in {
      val result = fillForm("otherName",Some(""))
      result.data("groupName") shouldBe "otherName"
      result.data("otherName") shouldBe ""
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "page.groups.groupName.noCompanyNameEntered"
    }
    "return errors for other radio button selected but just spaces in text box" in {
      val result = fillForm("otherName",Some("      "))
      result.data("groupName") shouldBe "otherName"
      result.data("otherName") shouldBe "      "
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "page.groups.groupName.noCompanyNameEntered"
    }
    "return errors for invalid name entered > 20 chars with trailing spaces" in {
      val result = fillForm("otherName",Some("Company Name that is longer than 20 chars"))
      result.data("groupName") shouldBe "otherName"
      result.data("otherName") shouldBe "Company Name that is longer than 20 chars"
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "page.groups.groupName.20CharsOrLess"
    }
    "return errors for name with dodgy characters (doesnt pass des regex)" in {
      val result = fillForm("otherName",Some("Name invalid$"))
      result.data("groupName") shouldBe "otherName"
      result.data("otherName") shouldBe "Name invalid$"
      result.errors.isEmpty shouldBe false
      result.errors.head.message shouldBe "page.groups.groupName.invalidFormat"
    }
  }
  "formGroupAddress" should {

  }
}