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

import models.CHROAddress

trait PPOBFixture extends AddressFixture {

  lazy val validCHROAddress = CHROAddress("Premises","address line 1",Some("address line 2"),"locality","Country",Some("Po Box"),Some("FX1 1ZZ"),Some("Region"))

  lazy val validPPOBFormDataWithROAddress =
    validAddressWithHouseNameFormData ++
    Seq(
      "addressGroup" -> "",
      "addressChoice" -> "RO",
      "action" -> "continue"
  )

  lazy val invalidPPOBFormData = validAddressWithHouseNameFormData ++ Seq(
    "addressChoice" -> ""
  )

  lazy val invalidPPOBHouseNameFormData = invalidAddressHouseNameFormData ++ Seq(
    "addressChoice" -> "RO"
  )

  lazy val invalidPPOBAddressLine1FormData = invalidAddressline1FormData ++ Seq(
    "addressChoice" -> "RO"
  )

  lazy val invalidPPOBAddressLine2FormData = invalidAddressline2FormData ++ Seq(
    "addressChoice" -> "RO"
  )

  lazy val invalidPPOBAddressLine3FormData = invalidAddressline3FormData ++ Seq(
    "addressChoice" -> "RO"
  )

  lazy val invalidPPOBAddressLine4FormData = invalidAddressline4FormData ++ Seq(
    "addressChoice" -> "RO"
  )
}
