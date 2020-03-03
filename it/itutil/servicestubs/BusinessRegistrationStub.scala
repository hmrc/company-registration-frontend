
package itutil.servicestubs

import com.github.tomakehurst.wiremock.stubbing.StubMapping
import itutil.WiremockHelper
import models.NewAddress
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.libs.json.{JsValue, Json}

trait BusinessRegistrationStub extends WiremockHelper {
  this: GuiceOneServerPerSuite =>

  private def busRegUrl(registrationId: String): String =
    s"/business-registration/$registrationId/addresses"

  def stubGetPrepopAddresses(registrationId: String,
                             status: Int,
                             addresses: Seq[NewAddress]
                            ): StubMapping = {
    val jsonBody = Json.obj("addresses" -> addresses)
    stubGet(busRegUrl(registrationId), status, Json.stringify(jsonBody))
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
