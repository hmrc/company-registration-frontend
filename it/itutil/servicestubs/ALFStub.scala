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
import org.scalatestplus.play.guice.GuiceOneServerPerSuite

trait ALFStub extends WiremockHelper {
  this: GuiceOneServerPerSuite =>

  def stubInitAlfJourney(redirectLocation: String): StubMapping =
    stubPost(
      url = "/api/v2/init",
      status = 200,
      responseBody = "{}",
      responseHeader = ("Location", redirectLocation)
    )

}
