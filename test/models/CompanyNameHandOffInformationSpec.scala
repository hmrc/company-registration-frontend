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

package models

import helpers.UnitSpec
import models.handoff.CompanyNameHandOffInformation
import play.api.libs.json.{JsObject, Json}
import java.time._

class CompanyNameHandOffInformationSpec extends UnitSpec {

  class Setup {
    val testModel = CompanyNameHandOffInformation("foo", LocalDateTime.of(1970,1,1,0,0,0,0), JsObject(Seq("foo" -> Json.toJson("bar"))))
    val json = """{"handoffType":"foo","handoffTime":"1970-01-01T00:00:00","data":{"foo":"bar"}}"""
  }

  "CompanyNameHandoffInformation" should {
    "be able to be parsed into a JSON structure" in new Setup {
      Json.toJson[CompanyNameHandOffInformation](testModel) mustBe Json.parse(json)
    }
  }
}
