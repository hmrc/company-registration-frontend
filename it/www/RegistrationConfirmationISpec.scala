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

package www

import itutil.IntegrationSpecBase
import play.api.http.HeaderNames
import play.api.libs.ws.WS

class RegistrationConfirmationISpec extends IntegrationSpecBase {

  private def client(path: String) = ws.url(s"http://localhost:$port/register-your-company$path").withFollowRedirects(false)

  "HO6" should {

    "Return a redirect to a new page when not authenticated" in {
      val response = await(client("/registration-confirmation?request=xxx").get())

      response.status shouldBe 303
      response.header(HeaderNames.LOCATION).get should include("/application-not-complete")
    }
  }
}