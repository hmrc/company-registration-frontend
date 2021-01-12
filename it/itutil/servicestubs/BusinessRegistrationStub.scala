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

package itutil.servicestubs

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.WiremockHelper
import models.NewAddress
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}

trait BusinessRegistrationStub extends WiremockHelper {
  this: GuiceOneServerPerSuite =>

  private def busRegUrl(registrationId: String): String =
    s"/business-registration/$registrationId/addresses"

  def stubGetPrepopAddresses(registrationId: String,
                             status: Int,
                             addresses: Seq[NewAddress]
                            ): StubMapping = {
    val jsonBody = Json.obj("addresses" -> addresses)
    stubGet(busRegUrl(registrationId), status, Json.stringify(jsonBody))
  }

  def stubGetPrepopAddresses(registrationId: String,
                             status: Int,
                             jsonAddress: JsValue
                            ): StubMapping = {

    stubGet(busRegUrl(registrationId), status, Json.stringify(jsonAddress))
  }

  def stubPrepopAddressPostResponse(registrationId: String,
                                    status: Int
                                   ): StubMapping = {

    stubPost(busRegUrl(registrationId), status, responseBody = "")
  }

}
