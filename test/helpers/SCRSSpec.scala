/*
 * Copyright 2019 HM Revenue & Customs
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

package helpers

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import mocks.SCRSMocks
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random

trait SCRSSpec extends UnitSpec with MockitoSugar with SCRSMocks with BeforeAndAfterEach with JsonHelpers {
  implicit val hc = HeaderCarrier()

  def cTDoc(status: String, groupBlock: String) = Json.parse(
    s"""
       | {
       |    "internalId" : "Int-f9bf61e1-9f5e-42b6-8676-0949fb1253e7",
       |    "registrationID" : "2971",
       |    "status" : "${status}",
       |    "formCreationTimestamp" : "2019-04-09T09:06:55+01:00",
       |    "language" : "en",
       |    "confirmationReferences" : {
       |        "acknowledgement-reference" : "BRCT00000000017",
       |        "transaction-id" : "000-434-2971"
       |    },
       |    "companyDetails" : {
       |        "companyName" : "Company Name Ltd",
       |        "cHROAddress" : {
       |            "premises" : "14",
       |            "address_line_1" : "St Test Walk",
       |            "address_line_2" : "Testley",
       |            "country" : "UK",
       |            "locality" : "Testford",
       |            "postal_code" : "TE1 1ST",
       |            "region" : "Testshire"
       |        },
       |        "pPOBAddress" : {
       |            "addressType" : "RO",
       |            "address" : {
       |                "addressLine1" : "14 St Test Walk",
       |                "addressLine2" : "Testley",
       |                "addressLine3" : "Testford",
       |                "addressLine4" : "Testshire",
       |                "postCode" : "TE1 1ST",
       |                "country" : "UK",
       |                "txid" : "93cf1cfc-75fd-4ac0-96ac-5f0018c70a8f"
       |            }
       |        },
       |        "jurisdiction" : "ENGLAND_AND_WALES"
       |    },
       |    "verifiedEmail" : {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }
       |    ${groupBlock}
       |}""".stripMargin)

  //Until CRFE is fully DI
  when(mockAppConfig.piwikURL).thenReturn(None)

  override def beforeEach() {
    resetMocks()

  }

  private val alpha = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

  def randStr(n:Int) = (1 to n).map(x => alpha(Random.nextInt.abs % alpha.length)).mkString
}

trait TestActorSystem {
  implicit val system = ActorSystem("test")
  implicit val materializer: Materializer = ActorMaterializer()
}