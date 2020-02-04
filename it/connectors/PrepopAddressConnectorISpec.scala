
package connectors

import itutil.IntegrationSpecBase
import itutil.servicestubs.BusinessRegistrationStub
import models.Address
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

class PrepopAddressConnectorISpec extends IntegrationSpecBase with BusinessRegistrationStub {

  lazy val connector: PrepopAddressConnector = app.injector.instanceOf[PrepopAddressConnector]
  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val testRegId = "12345"

  trait PrepopAddressTest {
    val addrLine1 = "line 1"
    val addrLine2 = "line 2"
    val addrLine3 = Some("line 3")
    val addrLine4 = Some("line 4")
    val postcode = Some("AB12 3YZ")
    val country = Some("United Kingdom")
    val testRegId = "12345"

    def prepopAddressSeq(n: Int): Seq[Address] =
      1 to n map (_ => Address(None, addrLine1, addrLine2, addrLine3, addrLine4, postcode, country))
  }

  s"GET /business-registration/$testRegId/addresses" should {
    "return Some(PrepopAddresses)" when {
      "there are addresses for the specified registration ID" in new PrepopAddressTest {
        val addressSeq = prepopAddressSeq(2)
        stubGetPrepopAddresses(testRegId, OK, Some(addressSeq))

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result shouldBe Some(PrepopAddresses(addressSeq))
      }
      "there aren't any addresses for the specified registration ID" in new PrepopAddressTest {
        stubGetPrepopAddresses(testRegId, NOT_FOUND, None)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result shouldBe Some(PrepopAddresses(Seq()))
      }
    }
    "return None" when {
      "the user is not authorised" in {
        stubGetPrepopAddresses(testRegId, FORBIDDEN, None)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result shouldBe None
      }
      "an unexpected error occurs" in {
        stubGetPrepopAddresses(testRegId, INTERNAL_SERVER_ERROR, None)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result shouldBe None
      }
      "the json is invalid" in new PrepopAddressTest {
        val addressJson = Json.obj()
        stubGetPrepopAddresses(testRegId, OK, addressJson)

        val result = await(connector.getPrepopAddresses(testRegId)(headerCarrier))

        result shouldBe None
      }
    }
  }

  s"POST /business-registration/$testRegId/addresses" should {
    "return OK" in new PrepopAddressTest {

      stubPrepopAddressPostResponse(testRegId, OK)

      val postData = Address(None, addrLine1, addrLine2, addrLine3, addrLine4, postcode, country)
      val result = await(connector.updatePrepopAddress(testRegId, postData))

      result shouldBe true
    }
    "return " in new PrepopAddressTest {
      stubPrepopAddressPostResponse(testRegId, INTERNAL_SERVER_ERROR)

      val postData = Address(None, addrLine1, addrLine2, addrLine3, addrLine4, postcode, country)
      val result = await(connector.updatePrepopAddress(testRegId, postData))

      result shouldBe false
    }
  }

}
