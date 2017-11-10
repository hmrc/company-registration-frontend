/*
 * Copyright 2017 HM Revenue & Customs
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

import fixtures.CompanyContactDetailsFixture
import helpers.{FormTestHelpers, SCRSSpec}
import models.CompanyContactViewModel
import play.api.data.FormError
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.util.Random

class CompanyContactFormSpec extends SCRSSpec with CompanyContactDetailsFixture with FormTestHelpers with WithFakeApplication {

  "Creating a form with valid data" should {
    "have no errors" in  {
      val request = FakeRequest().withFormUrlEncodedBody(validCompanyContactDetailsFormData:_*)

      CompanyContactForm.form.bind(validCompanyContactDetailsFormData.toMap).value shouldBe
        Some(CompanyContactViewModel("testFirstName testMiddleName testLastName", Some("foo@bar.wibble"), Some("0123 345 6789"), Some("07123 456789")))
    }
  }

  "Creating a form with invalid data" when {

    val form = companyContactForm

    "the email field is invalid" should {
      "have an error" in {
        bindFromRequestWithErrors(invalidCompanyContactDetailsEmailFormData, companyContactForm).map(x => (x.key, x.message))
          List(("contactEmail", "Enter a valid email address"))
      }
    }

    "the email field is more than 70 cahracters" should {
      "have an error" in {
        bindFromRequestWithErrors(invalidCompanyContactDetailsEmailTooLong, companyContactForm).map(x => (x.key, x.message))
        List(("contactEmail", "Enter an email address using 70 characters or less"))
      }
    }

    "the name field is invalid" should {
      "have an error" in {
        bindFromRequestWithErrors(invalidCompanyContactDetailsNameFormData, companyContactForm).map(x => (x.key, x.message)) shouldBe
          List(("contactName", "Tell us the contact name using only letters (without accents), hyphens and apostrophes"))
      }
    }
    "the contact number field is invalid" should {
      "have an error" in {
        bindFromRequestWithErrors(invalidCompanyContactDetailsDaytimeTelephoneFormData, companyContactForm).map(x => (x.key, x.message)) shouldBe
          List(("contactDaytimeTelephoneNumber", "validation.contactNum"))
      }
    }
    "the other contact number field is invalid" should {
      "have an error" in {
        bindFromRequestWithErrors(invalidCompanyContactDetailsMobileFormData, companyContactForm).map(x => (x.key, x.message)) shouldBe
          List(("contactMobileNumber", "validation.contactNum"))
        }
      }

    "The name does not contain a middle name or a surname" should {

      "contain an error" in {
        val data = buildContactDetailsFormData(name = "test")
        val error = Seq(FormError("contactName",  "A surname is required"))

        assertFormError(form, data, error)
      }
    }

    "only contact name is provided" should {

      "return a 'choose at least one' error" in {
        val data = buildContactDetailsFormData(email = "", telephoneNumber = "", mobileNumber = "")
        val error = Seq(FormError("", "Enter at least one of the following contact details", Seq("chooseOne")))

        assertFormError(form, data, error)
      }
    }
  }

  "A successful response" should {

    val form = CompanyContactForm.form

    "be returned" when {

      "only an email is presented" in {
        val email = "foo@bar.wibble"
        val formData = Map(
          "contactName" -> "test name",
          "contactEmail" -> email,
          "contactDaytimeTelephoneNumber" -> "",
          "contactMobileNumber" -> "")

        form.bind(formData).value shouldBe Some(CompanyContactViewModel("test name", Some(email), None, None))
      }

      "an email is presented with a 10 character mail server and top-level domain" in {
        val email = "foo@barrrrrrrr.wibbbbbble"
        val formData = Map(
          "contactName" -> "test name",
          "contactEmail" -> email,
          "contactDaytimeTelephoneNumber" -> "",
          "contactMobileNumber" -> "")

        form.bind(formData).value shouldBe Some(CompanyContactViewModel("test name", Some(email), None, None))
      }

      "only a daytime phone number is presented" in {
        val formData = Map(
          "contactName" -> "test name",
          "contactEmail" -> "",
          "contactDaytimeTelephoneNumber" -> "02123456789",
          "contactMobileNumber" -> "")

        form.bind(formData).value shouldBe Some(CompanyContactViewModel("test name", None, Some("02123456789"), None))
      }

      "only a mobile phone number is presented" in {
        val formData = Map(
          "contactName" -> "test name",
          "contactEmail" -> "",
          "contactDaytimeTelephoneNumber" -> "",
          "contactMobileNumber" -> "07123456789")

        form.bind(formData).value shouldBe Some(CompanyContactViewModel("test name", None, None, Some("07123456789")))
      }
    }
  }

  "Company contact name" should {

    val form = CompanyContactForm.form
    val contactNameFirst101 = randStr(101) + " " + "middle" + " " + "surname"
    val contactNameFirst100 = randStr(100) + " " + "middle" + " " + "surname"
    val contactNameMiddle101 = "first" + " " + randStr(101) + " " + "surname"
    val contactNameMiddle100 = "first" + " " + randStr(100) + " " + "surname"
    val contactSurname101 = "first" + " " + "middle" + " " + randStr(101)
    val contactSurname100 = "first" + " " + "middle" + " " + randStr(100)

    val validContactEmail = "foo@bar.wibble"
    val validContactDaytimeTelephoneNumber = "0123 456 7890"
    val validContactMobileNumber = "07123 456 789"

    "will be successfully displayed" when {

      "the first name has a length of exactly 100 characters" in {

        val formData = Map(
          "contactName" -> contactNameFirst100,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )

        assertFormSuccess(form, formData)
      }

      "the middle name has a length of exactly 100 characters" in {

        val formData = Map(
          "contactName" -> contactNameFirst100,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )

        assertFormSuccess(form, formData)
      }

      "the surname has a length of exactly 100 characters" in {

        val formData = Map(
          "contactName" -> contactNameFirst100,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )

        assertFormSuccess(form, formData)
      }

      "the name starts with a space" in {
        val name = " Foo B"
        val formData = Map(
          "contactName" -> name,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            name,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }

      "the name has a ' in it" in {
        val name = "Foo O'Bar"
        val formData = Map(
          "contactName" -> name,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            name,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }

      "the name has a - in it" in {
        val name = "Foo Bar-Wibble"
        val formData = Map(
          "contactName" -> name,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            name,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }

      "the name is triple barrelled" in {
        val name = "Foo Bar-Wibble-Nermal"
        val formData = Map(
          "contactName" -> name,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            name,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }

    }

    "throw an error" when {

      "the name has a number in it" in {
        val formData = Map(
          "contactName" -> "Foo O'Bar the 3rd",
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )

        val error = Seq(FormError("contactName", "Tell us the contact name using only letters (without accents), hyphens and apostrophes"))
        assertFormError(form, formData, error)
      }

      "contains an illegal character of $" in {
        val formData = Map(
          "contactName" -> "$Contact name",
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactName").map(x => (x.key, x.message)) shouldBe
          Some(("contactName" ,"Tell us the contact name using only letters (without accents), hyphens and apostrophes"))
      }

      "contains invalid characters" in {
        val formData = Map(
          "contactName" -> "Tést Füll Nàme",
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactName").map(x => (x.key, x.message)) shouldBe
          Some(("contactName", "Tell us the contact name using only letters (without accents), hyphens and apostrophes"))
      }

      "contains .'s in name" in {
        val formData = Map(
        "contactName" -> "F.oo .Bar W.ibble",
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
        "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactName").map(x => (x.key, x.message)) shouldBe
          Some(("contactName" ,"Enter a name without using full stops"))
      }

      "contains an illegal character of #" in {
        val formData = Map(
          "contactName" -> "Foo Bar #1",
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactName").map(x => (x.key, x.message)) shouldBe
          Some(("contactName" ,"Tell us the contact name using only letters (without accents), hyphens and apostrophes"))
      }

      "the first name has a length of over 100 characters (101)" in {
        val formData = Map(
          "contactName" -> contactNameFirst101,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        val error = Seq(FormError("contactName", "Enter a first name that’s 100 characters or less"))
        assertFormError(form, formData, error)
      }

      "the middle name has a length of over 100 characters (101)" in {
        val formData = Map(
          "contactName" -> contactNameMiddle101,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        val error = Seq(FormError("contactName", "Enter a middle name that’s 100 characters or less"))
        assertFormError(form, formData, error)
      }

      "the surname has a length of over 100 characters (101)" in {
        val formData = Map(
          "contactName" -> contactSurname101,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        val error = Seq(FormError("contactName", "Enter a last name that’s 100 characters or less"))
        assertFormError(form, formData, error)
      }

      "Single character name" in {
        val formData = Map(
          "contactName" -> "A",
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        val error = Seq(FormError("contactName", "A surname is required"))
        assertFormError(form, formData, error)
      }
    }
  }

  "Company email address" should {
    val form = CompanyContactForm.form
    val validContactName = "Foo B"
    val contactEmail70 = "abcdefghijklmnopqrstuvwxyz1234abcdefghijklmnopqrstuvwxyz1234@abcde.com"
    val contactEmail71 = "abcdefghijklmnopqrstuvwxyz1234abcdefghijklmnopqrstuvwxyz1234@abcdef.com"
    val validContactDaytimeTelephoneNumber = "0123 456 7890"
    val validContactMobileNumber = "07123 456 789"


    "throw an error" when {

      "an email is presented with a single word after the @ symbol" in {
        val formData = Map(
          "contactName" -> "test name",
          "contactEmail" -> "foo@barrrrrrrr",
          "contactDaytimeTelephoneNumber" -> "",
          "contactMobileNumber" -> "")

        val error = Seq(FormError("contactEmail", "Enter a valid email address"))
        assertFormError(form, formData, error)
      }

      "it contains a +" in {
        val email = "bar+foo@bar.wibble"
        val data = Map(
          "contactName" -> validContactName,
          "contactEmail" -> email,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        val error = Seq(FormError("contactEmail", "Enter a valid email address"))
        assertFormError(form, data, error)
      }

      "contains an illegal character of $" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> "$foo@bar.wibble",
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactEmail").map(x => (x.key, x.message)) shouldBe Some(("contactEmail", "Enter a valid email address"))
      }
      "When it contains a single @ symbol but is 71 characters in length" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> contactEmail71,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactEmail").map(x => (x.key, x.message)) shouldBe Some(("contactEmail", "Enter an email address using 70 characters or less"))
      }
      "When it contains more than one @ symbol" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> "foo@@bar.wibble",
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactEmail").map(x => (x.key, x.message)) shouldBe Some(("contactEmail", "Enter a valid email address"))
      }
    }

    "be successfully displayed" when {

      "it contains a -" in {
        val email = "bar-foo@bar.wibble"
        val data = Map(
          "contactName" -> validContactName,
          "contactEmail" -> email,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        assertFormSuccess(form, data)
      }

      "When it contains a single @ symbol and is 70 characters in length" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> contactEmail70,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some(contactEmail70),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }
      "When it contains a single @ symbol ends with multiple . symbols separating the domain name" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> "foo@bar.wibble.abc.def",
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some("foo@bar.wibble.abc.def"),
            Some(validContactDaytimeTelephoneNumber),
            Some(validContactMobileNumber)
          )
      }
    }
  }
  "The Company contact number" should {
    val form = CompanyContactForm.form
    val validContactName = "Daveo G"
    val validContactEmail = "goodemail@email.com"
    val validContactDaytimeTelephoneNumber = "0123 456 7890"
    val validContactMobileNumber = "07123 456 789"

    "will be successfully displayed" when {
      "The number contains 20 digits" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> "01234567890123456789",
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some(validContactEmail),
            Some("01234567890123456789"),
            Some(validContactMobileNumber)
          )
      }

      "The number contains a space between the area code and the number" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> "01234 567890",
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some(validContactEmail),
            Some("01234 567890"),
            Some(validContactMobileNumber)
          )
      }


    }

    "throw an error" when {
      "it contains an illegal character of $" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> "$0123 456 7890",
          "contactMobileNumber" -> validContactMobileNumber
        )
        form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum"))
      }
    }

    "it contains just letters" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "ABCDE",
        "contactMobileNumber" -> validContactMobileNumber
      )
      form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum"))
    }

    "it contains letters and numbers" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "ABC 123",
        "contactMobileNumber" -> validContactMobileNumber
      )
      form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum"))
    }

    "it exceeds 20 digits" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "123456789012345678901",
        "contactMobileNumber" -> validContactMobileNumber
      )
      form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum.tooLong"))
    }

    "it contains brackets" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "(01234) 567 890",
        "contactMobileNumber" -> validContactMobileNumber
      )
      form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum"))
    }

    "it has 6 digits with no area code" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "123456",
        "contactMobileNumber" -> "0123456789012"
      )
      form.bind(formData).error("contactDaytimeTelephoneNumber").map(x => (x.key, x.message)) shouldBe Some(("contactDaytimeTelephoneNumber" ,"validation.contactNum.tooShort"))
    }
  }

  "The Company other contact number" should {
    val form = CompanyContactForm.form
    val validContactName = "Foo B"
    val validContactEmail = "foo@bar.wibble"
    val validContactDaytimeTelephoneNumber = "0123 456 7890"
    val validContactMobileNumber = "07123 456 789"

    "will be successfully displayed" when {
      "The number contains 20 digits" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> "01234567890123456789"
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some("01234567890123456789")
          )
      }

      "will be successfully displayed" when {
        "The number contains 20 digits with spaces" in {
          val formData = Map(
            "contactName" -> validContactName,
            "contactEmail" -> validContactEmail,
            "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
            "contactMobileNumber" -> "01234567890 12345 6789"
          )
          form.bind(formData).value.get shouldBe
            CompanyContactViewModel(
              validContactName,
              Some(validContactEmail),
              Some(validContactDaytimeTelephoneNumber),
              Some("01234567890123456789")
            )
        }
      }
      "The number contains a space between the area code and the number" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> "07123 456789"
        )
        form.bind(formData).value.get shouldBe
          CompanyContactViewModel(
            validContactName,
            Some(validContactEmail),
            Some(validContactDaytimeTelephoneNumber),
            Some("07123 456789")
          )
      }
    }

    "throw an error" when {
      "it contains an illegal character of $" in {
        val formData = Map(
          "contactName" -> validContactName,
          "contactEmail" -> validContactEmail,
          "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
          "contactMobileNumber" -> "$07123 456789"
        )
        form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum"))
      }
    }

    "it contains just letters" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
        "contactMobileNumber" -> "ABCde"
      )
      form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum"))
    }

    "it contains letters and numbers" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
        "contactMobileNumber" -> "07123 abcDE"
      )
      form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum"))
    }

    "it exceeds 20 digits" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
        "contactMobileNumber" -> "012345678901234567890"
      )
      form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum.tooLong"))
    }

    "it has 6 digits for mobile number" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> "1234567890",
        "contactMobileNumber" -> "01234"
      )
      form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum.tooShort"))
    }

    "it contains brackets" in {
      val formData = Map(
        "contactName" -> validContactName,
        "contactEmail" -> validContactEmail,
        "contactDaytimeTelephoneNumber" -> validContactDaytimeTelephoneNumber,
        "contactMobileNumber" -> "(07123) 456 789"
      )
      form.bind(formData).error("contactMobileNumber").map(x => (x.key, x.message)) shouldBe Some(("contactMobileNumber" ,"validation.contactNum"))
    }
  }
}
