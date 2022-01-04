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

package fixtures

import models.{Address, CHROAddress, NewAddress}

trait AddressFixture {

	lazy val validNewAddress = NewAddress(
		"testLine1",
		"testLine2",
		None,
		None,
		Some("FX1 1ZZ"),
		None,
		None
	)

	lazy val validNewAddress2 = NewAddress(
		"address2testLine1",
		"address2testLine2",
		None,
		None,
		Some("FX1 1ZZ"),
		None,
		None
	)

	lazy val validNewAddress3 = NewAddress(
		"address3testLine1",
		"address3testLine2",
		None,
		None,
		Some("FX1 1ZZ"),
		None,
		None
	)

	lazy val validAddressWithHouseName = Address(
		Some("testHouseNumber"),
		"testStreet",
		"testArea",
		Some("testPostTown"),
		Some("testRegion"),
		Some("FX1 1ZZ"),
		None
	)

	lazy val validAddressFromLookup = Address(
		Some("1234"),
		"testStreet",
		"testArea",
		Some("testPostTown"),
		Some("testRegion"),
		Some("FX1 1ZZ"),
    Some("UK"),
    Some("uprn-01234"),
    "txid-01234"
	)

	lazy val chROAddress = CHROAddress(
		premises = "1234",
    address_line_1 = "testAddressLine1",
    address_line_2 = Some("testAddressLine2"),
    locality = "testLocality",
    country = "UK",
    po_box = None,
    postal_code = Some("FX1 1ZZ"),
    region = Some("testRegion")
	)

	lazy val validAddressWithHouseNameFormData = Seq(
		"address.houseNameNumber" -> "testHouseNumber",
		"address.street" -> "testStreet",
		"address.area" -> "testArea",
		"address.postTown" -> "testPostTown",
		"address.region" -> "testRegion",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")

	lazy val invalidAddressHouseNameFormData = Seq(
		"address.houseNameNumber" -> "!!",
		"address.street" -> "testStreet",
		"address.area" -> "testArea",
		"address.postTown" -> "testPostTown",
		"address.region" -> "testRegion",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")

	lazy val invalidAddressline1FormData = Seq(
		"address.houseNameNumber" -> "testHouseNumber",
		"address.street" -> "!!",
		"address.area" -> "testArea",
		"address.postTown" -> "testPostTown",
		"address.region" -> "testRegion",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")

	lazy val invalidAddressline2FormData = Seq(
		"address.houseNameNumber" -> "testHouseNumber",
		"address.street" -> "testStreet",
		"address.area" -> "!!",
		"address.postTown" -> "testPostTown",
		"address.region" -> "testRegion",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")
	lazy val invalidAddressline3FormData = Seq(
		"address.houseNameNumber" -> "testHouseNumber",
		"address.street" -> "testStreet",
		"address.area" -> "testArea",
		"address.postTown" -> "!!",
		"address.region" -> "testRegion",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")
	lazy val invalidAddressline4FormData = Seq(
		"address.houseNameNumber" -> "testHouseNumber",
		"address.street" -> "testStreet",
		"address.area" -> "testArea",
		"address.postTown" -> "testPostTown",
		"address.region" -> "!!",
		"address.postCode" -> "FX1 1ZZ",
		"address.country" -> "testCountry")
}
