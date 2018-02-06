
package fixtures

import models.{CHROAddress, HandBackPayloadModel}
import models.handoff.{CompanyNameHandOffIncoming, NavLinks, SummaryPage1HandOffIncoming}
import play.api.libs.json.{JsObject, JsValue, Json}
import utils.JweEncryptor

trait HandOffFixtures {

  val userId: String
  val regId: String

  private val jwe = new JweEncryptor {
    override protected val key: String = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"
  }

  val HO2_MODEL = CompanyNameHandOffIncoming(
    Some(regId),
    userId,
    "TestCompanyName",
    CHROAddress(
      "Premises",
      "Line 1,",
      Some("Line 2,"),
      "Locality",
      "Country",
      Some(""),
      Some("FX1 1ZZ"),
      Some("")
    ),
    "testJurisdiction",
    Json.obj(),
    Json.obj(),
    Json.parse("""{"forward":"testForward","reverse":"testReverse"}""").as[JsObject]
  )

  val HO4_MODEL = SummaryPage1HandOffIncoming(
    userId,
    regId,
    Json.obj(),
    Json.obj(),
    NavLinks("testForwardLink", "testReverseLink")
  )

  val HO1B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get
  val HO2_PAYLOAD = jwe.encrypt[CompanyNameHandOffIncoming](HO2_MODEL).get
  val HO3B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get
  val HO4_PAYLOAD = jwe.encrypt[SummaryPage1HandOffIncoming](HO4_MODEL).get
  val HO5B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get
}
