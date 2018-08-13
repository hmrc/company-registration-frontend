
package fixtures

import models.CHROAddress
import models.handoff._
import play.api.libs.json.{JsObject, JsValue, Json}
import utils.{JweDecryptor, JweEncryptor}

trait HandOffFixtures {

  val userId: String
  val regId: String

  private val jwe = new JweEncryptor with JweDecryptor {
    override protected val key: String = "Fak3-t0K3n-f0r-pUBLic-r3p0SiT0rY"
  }

  lazy val HO2_MODEL = CompanyNameHandOffIncoming(
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
  lazy val H03_1_MODEL_SHAREHOLDERS_FLAG = GroupHandBackModel(
    userId,
    regId,
    Json.obj("testCHBagKey" -> "testValue"),
    Json.obj(),
    NavLinks("/forwardToNextCohoPage","/backToPreviousCohoPage"),
    Some(true)
  )

  lazy val H03_1_MODEL_NO_SHAREHOLDERS_FLAG = GroupHandBackModel(
    userId,
    regId,
    Json.obj(),
    Json.obj(),
    NavLinks("/forwardToNextCohoPage","/backToPreviousCohoPage"),
    None
  )

  lazy val HO4_MODEL = SummaryPage1HandOffIncoming(
    userId,
    regId,
    Json.obj(),
    Json.obj(),
    NavLinks("testForwardLink", "testReverseLink")
  )

  val HO1B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get
  lazy val HO2_PAYLOAD = jwe.encrypt[CompanyNameHandOffIncoming](HO2_MODEL).get
  val HO3B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get
  lazy val H03_1_PAYLOAD_FLAG = jwe.encrypt[GroupHandBackModel](H03_1_MODEL_SHAREHOLDERS_FLAG).get
  lazy val H03_1_PAYLOAD_NO_FLAG = jwe.encrypt[GroupHandBackModel](H03_1_MODEL_NO_SHAREHOLDERS_FLAG).get
  lazy val HO4_PAYLOAD = jwe.encrypt[SummaryPage1HandOffIncoming](HO4_MODEL).get
  val HO5B_PAYLOAD = jwe.encrypt[JsValue](Json.parse("""{"test":"json"}""")).get

  val handOffNavModelDataUpTo3 = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender1",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )
  val handOffNavModelDataUpTo3_1 = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender1",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        ),
        "3-1" -> NavLinks(
          "/forwardToNextCohoPage",
          "/backToPreviousCohoPage"
          )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )
  val handOffNavModelDataWithJust3_2Requirements = HandOffNavModel(
    Sender(Map(
        "3-2" -> NavLinks(
          "/forwardToNextHmrcPage",
          "/reverseToPreviousHmrcPage"
        )
      )
    ),
    Receiver(
      Map(
        "3-1" -> NavLinks(
          "/forwardToNextCohoPage",
          "/backToPreviousCohoPage"
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )

  val handOffNavModelUpdatedUpTo3 = HandOffNavModel(
    Sender(
      Map(
        "1" -> NavLinks(
          "testForwardLinkFromSender17373737373",
          "testReverseLinkFromSender1"
        ),
        "3" -> NavLinks(
          "testForwardLinkFromSender3",
          "testReverseLinkFromSender3"
        )
      )
    ),
    Receiver(
      Map(
        "0" -> NavLinks(
          "testForwardLinkFromReceiver0",
          "testReverseLinkFromReceiver0"
        ),
        "2" -> NavLinks(
          "testForwardLinkFromReceiver2",
          "testReverseLinkFromReceiver2"
        )
      ),
      Map("testJumpKey" -> "testJumpLink"),
      Some(Json.parse("""{"testCHBagKey": "testValue"}""").as[JsObject])
    )
  )



}
