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

import models.UserDetailsModel

trait UserDetailsFixture extends JweFixture {

  val userDetailsModel = UserDetailsModel(
    "testFirstName testMiddleName",
    "testEmail",
    "Organisation",
    Some("testDescription"),
    Some("testLastName"),
    Some("testDOB"),
    Some("testPostcode"),
    "testAuthProviderID",
    "testAuthProviderType")

  val encryptedUserDetails = JweWithTestKey.encrypt[UserDetailsModel](userDetailsModel)

  val testUserDetailsLink = "/user-details/someUserDetails"
}
