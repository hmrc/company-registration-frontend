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

package fixtures

import forms.CompanyContactForm
import models.{CompanyContactDetails, CompanyContactDetailsApi, Links}
import play.api.libs.json.Json
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.language.implicitConversions

trait CompanyContactDetailsFixture {

  lazy val companyContactForm = CompanyContactForm.form

  lazy val companyContactModelFromUserDetails = CompanyContactDetailsApi(
    Some("verified@email"),
    None,
    None
  )

  lazy val companyContactFormData = Seq(
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "01234566789",
    "contactMobileNumber" -> "01234566789")

  lazy val validCompanyContactDetailsModel = CompanyContactDetailsApi(
    Some("foo@bar.wibble"),
    Some("0123456789"),
    Some("0123456789"))

  lazy val validCompanyContactDetailsResponse = CompanyContactDetails(
    Some("foo@bar.wibble"),
    Some("0123456789"),
    Some("0123456789"),
    Links(Some("testLink")))

  lazy val companyContactDetailsCacheMap = CacheMap("", Map("" -> Json.toJson("")))

  lazy val validCompanyContactDetailsFormData = Seq(
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123 345 6789",
    "contactMobileNumber" -> "07123 456789")

  lazy val invalidCompanyContactDetailsNameFormData = Seq(
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "01",
    "contactMobileNumber" -> "0123456")

  lazy val invalidCompanyContactDetailsEmailFormData = Seq(
    "contactEmail" -> "test",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789")

  lazy val invalidCompanyContactDetailsDaytimeTelephoneFormData = Seq(
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123456789dsdsdd",
    "contactMobileNumber" -> "0123456789")

  lazy val invalidCompanyContactDetailsMobileFormData = Seq(
    "contactEmail" -> "foo@bar.wibble",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789sdsddsd")

  lazy val invalidCompanyContactDetailsEmailTooLong = Seq(
    "contactEmail" -> "uiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiuiu.com",
    "contactDaytimeTelephoneNumber" -> "0123456789",
    "contactMobileNumber" -> "0123456789")
}