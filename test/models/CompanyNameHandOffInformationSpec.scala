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

package models

import models.handoff.CompanyNameHandOffInformation
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.play.test.UnitSpec

class CompanyNameHandOffInformationSpec extends UnitSpec {

  class Setup {
    val testModel = CompanyNameHandOffInformation("foo", new DateTime(0, DateTimeZone.UTC), JsObject(Seq("foo" -> Json.toJson("bar"))))
    val json = """{"handoffType":"foo","handoffTime":"1970-01-01T00:00:00.000Z","data":{"foo":"bar"}}"""
  }

  "CompanyNameHandoffInformation" should {
    "be able to be parsed into a JSON structure" in new Setup {
      Json.toJson[CompanyNameHandOffInformation](testModel) shouldBe Json.parse(json)
    }
  }
}
