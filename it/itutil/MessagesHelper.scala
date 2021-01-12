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

package itutil

import java.util.Locale

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.{Lang, MessagesApi}

trait MessagesHelper {

    self: IntegrationSpecBase =>

    val mockMessagesApi = app.injector.instanceOf[MessagesApi]
    val mockMessages = mockMessagesApi.preferred(Seq(Lang(Locale.ENGLISH)))

    def messages(key: String)(implicit app: Application) = mockMessages(key)
}
