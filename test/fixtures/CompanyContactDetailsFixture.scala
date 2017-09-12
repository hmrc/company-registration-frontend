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

package fixtures

import forms.CompanyContactForm
import models.{Links, CompanyContactDetails, CompanyContactViewModel}
import play.api.libs.json.Json
import uk.gov.hmrc.http.cache.client.CacheMap
import scala.language.implicitConversions

trait CompanyContactDetailsFixture {

  lazy val companyContactForm = CompanyContactForm.form

  def buildContactDetailsFormData(name: String = "test name",
                                  email: String = "foo@bar.wibble",
                                  telephoneNumber: String = "0123456789",
                                  mobileNumber: String = "0123456789"): Map[String, String] = {
    Map(
      "contactName" -> name,
      "contactEmail" -> email,
      "contactDaytimeTelephoneNumber" -> telephoneNumber,
      "contactMobileNumber" -> mobileNumber)
  }

  lazy val companyContactModelFromUserDetails = CompanyContactViewModel(
    "testFirstName testMiddleName testLastName",
    Some("testEmail"),
    None,
    None
  )

  lazy val companyContactFormData = Seq(
    "contactFirstName" -> "testFirstName",
    "contactMiddleName" -> "testMiddleName",
    "contactLastName" -> "testLastName",
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "01234566789",
    "contactMobileNumber" -> "01234566789")

  lazy val validCompanyContactDetailsModel = CompanyContactViewModel(
    "testFirstName testMiddleName testLastName",
    Some("foo@bar.wibble"),
    Some("0123456789"),
    Some("0123456789"))

  lazy val validCompanyContactDetailsResponse = CompanyContactDetails(
    Some("testFirstName"),
    Some("testMiddleName"),
    Some("testLastName"),
    Some("0123456789"),
    Some("0123456789"),
    Some("foo@bar.wibble"),
    Links(Some("testLink")))

  lazy val companyContactDetailsCacheMap = CacheMap("", Map("" -> Json.toJson("")))

  lazy val validCompanyContactDetailsFormData = Seq(
    "contactName" -> "testFirstName testMiddleName testLastName",
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123 345 6789",
    "contactMobileNumber" -> "07123 456789")

  lazy val invalidCompanyContactDetailsNameFormData = Seq(
    "contactName" -> "testFirstName1212$ testMiddleName$ testLastName",
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789")

  lazy val invalidCompanyContactDetailsEmailFormData = Seq(
    "contactName" -> "testFirstName testMiddleName testLastName",
    "contactEmail" -> "test",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789")

  lazy val invalidCompanyContactDetailsDaytimeTelephoneFormData = Seq(
    "contactName" -> "testFirstName testMiddleName testLastName",
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123456789dsdsdd",
    "contactMobileNumber" -> "0123456789")

  lazy val invalidCompanyContactDetailsMobileFormData = Seq(
    "contactName" -> "testFirstName testMiddleName testLastName",
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789sdsddsd")

  lazy val invalidCompanyContactDetailsFormData = Seq(
    "contactName" -> "&&£",
    "contactEmail" -> "testemail.co.uk",
    "contactDaytimeTelephoneNumber" -> "abc",
    "contactMobileNumber" -> "abc")

  lazy val invalidCompanyContactDetailsEmptyFormData = Seq(
    "contactName" -> "",
    "contactEmail" -> "",
    "contactDaytimeTelephoneNumber" -> "",
    "contactMobileNumber" -> "")


  lazy val invalidCompanyContactDetailsEmailTooLong = Seq(
    "contactName" -> "testFirstName testMiddleName testLastName",
    "contactEmail" -> "uiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiu.com",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789")

}
