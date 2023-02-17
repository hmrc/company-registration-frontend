/*
 * Copyright 2023 HM Revenue & Customs
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

import java.net.URLEncoder

trait LoginFixture {
  lazy val authUrl = s"http://localhost:9553/bas-gateway/sign-in?continue_url=${URLEncoder.encode(s"http://localhost:9970/register-your-company/post-sign-in", "UTF-8")}&origin=company-registration-frontend"
  def authUrl(handOffID: String, payload: String) = s"http://localhost:9553/bas-gateway/sign-in?continue_url=${URLEncoder.encode(s"http://localhost:9970/register-your-company/post-sign-in?handOffID=$handOffID&payload=$payload", "UTF-8")}&origin=company-registration-frontend"
}
