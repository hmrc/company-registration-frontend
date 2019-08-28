
package itutil.servicestubs

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import connectors.PrepopAddresses
import itutil.WiremockHelper
import models.Address
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.json.{JsValue, Json}

trait BusinessRegistrationStub extends WiremockHelper { this: OneServerPerSuite =>

  private def busRegUrl(registrationId: String): String =
    s"/business-registration/$registrationId/addresses"

  def stubGetPrepopAddresses(registrationId: String,
                             status: Int,
                             optAddresses: Option[Seq[Address]]
                            ): StubMapping = {
    val addresses = optAddresses match {
      case Some(addressValues) => addressValues
      case None => Seq()
    }

    val jsonBody = Json.stringify(Json.toJson(PrepopAddresses(addresses)))
    stubGet(busRegUrl(registrationId), status, jsonBody)
  }

  def stubGetPrepopAddresses(registrationId: String,
                             status: Int,
                             jsonAddress: JsValue
                            ): StubMapping = {

    stubGet(busRegUrl(registrationId), status, Json.stringify(jsonAddress))
  }

  def stubPrepopAddressPostResponse(registrationId: String,
                                    status: Int
                                   ): StubMapping = {

    stubPost(busRegUrl(registrationId), status, responseBody = "")
  }

}
