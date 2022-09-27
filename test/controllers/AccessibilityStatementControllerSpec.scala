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

package controllers

import helpers.SCRSSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import views.html.help.accessibility_statement

class AccessibilityStatementControllerSpec extends SCRSSpec with GuiceOneAppPerSuite {

  object TestAccessibilityStatementController extends AccessibilityStatementController(
    app.injector.instanceOf[MessagesControllerComponents],
    app.injector.instanceOf[accessibility_statement]
  )

  val testGetRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/accessibility-statement")

  "show" should {
    "return OK" in {
      val result = TestAccessibilityStatementController.show("test")(testGetRequest)
      status(result) mustBe Status.OK
      contentType(result) mustBe Some("text/html")
      charset(result) mustBe Some("utf-8")
    }
  }

}