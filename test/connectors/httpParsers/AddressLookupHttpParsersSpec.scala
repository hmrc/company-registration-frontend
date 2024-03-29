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

package connectors.httpParsers

import ch.qos.logback.classic.Level
import connectors.ALFLocationHeaderNotSetException
import connectors.httpParsers.exceptions.DownstreamExceptions
import helpers.SCRSSpec
import models.{Address, NewAddress}
import play.api.http.HeaderNames
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.http.HttpResponse
import utils.LogCapturingHelper

class AddressLookupHttpParsersSpec extends SCRSSpec with LogCapturingHelper {

  val addressId = "12345"

  val testAddress = NewAddress(
    "testLine1",
    "testLine2",
    Some("testLine3"),
    Some("testLine4"),
    Some("FX1 1ZZ"),
    None
  )

  val testAddressJson = Json.obj(
    "address" -> Json.obj(
      "lines" -> Seq(
        "testLine1",
        "testLine2",
        "testLine3",
        "testLine4"
      ),
      "postcode" -> "FX1 1ZZ",
      "auditRef" -> None
    )
  )

  "AddressLookupHttpParsers" when {

    "calling .httpAddressReads" when {

      "response is 2xx and JSON is valid" must {

        "return an Address" in {

          AddressLookupHttpParsers.addressHttpReads.read("", "", HttpResponse(OK, json = testAddressJson, Map())) mustBe testAddress
        }
      }

      "response is 2xx and JSON is malformed" must {

        "return a JsResultException and log an error" in {

          withCaptureOfLoggingFrom(AddressLookupHttpParsers.logger) { logs =>
            intercept[JsResultException](AddressLookupHttpParsers.addressHttpReads.read("", "", HttpResponse(OK, json = Json.obj(), Map())))
            logs.containsMsg(Level.ERROR, "[AddressLookupHttpParsers][addressHttpReads] NewAddress returned from ALF could not be parsed to Address model")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an AddressLookupException and log an error" in {

          withCaptureOfLoggingFrom(AddressLookupHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.AddressLookupException](AddressLookupHttpParsers.addressHttpReads.read("", "/address/1234", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[AddressLookupHttpParsers][addressHttpReads] Calling url: '/address/1234' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }

    "calling .onRampHttpReads" when {

      "response is 2xx and a Location header is present" must {

        "return the Location header value" in {

          val location = "/foo/bar/wizz"

          AddressLookupHttpParsers.onRampHttpReads.read("", "",
            HttpResponse(ACCEPTED, "", Map(HeaderNames.LOCATION -> Seq(location)))
          ) mustBe location
        }
      }

      "response is 2xx and NO Location header value is present" must {

        "return a ALFLocationHeaderNotSetException and log an ERROR" in {

          withCaptureOfLoggingFrom(AddressLookupHttpParsers.logger) { logs =>
            intercept[ALFLocationHeaderNotSetException](AddressLookupHttpParsers.onRampHttpReads.read("", "", HttpResponse(ACCEPTED, "")))
            logs.containsMsg(Level.ERROR, "[AddressLookupHttpParsers][onRampHttpReads] Location header not set in AddressLookup response")
          }
        }
      }

      "response is any other status, e.g ISE" must {

        "return an AddressLookupException and log an error" in {

          withCaptureOfLoggingFrom(AddressLookupHttpParsers.logger) { logs =>
            intercept[DownstreamExceptions.AddressLookupException](AddressLookupHttpParsers.onRampHttpReads.read("", "/address/1234", HttpResponse(INTERNAL_SERVER_ERROR, "")))
            logs.containsMsg(Level.ERROR, s"[AddressLookupHttpParsers][onRampHttpReads] Calling url: '/address/1234' returned unexpected status: '$INTERNAL_SERVER_ERROR'")
          }
        }
      }
    }
  }
}
