
package fixtures

trait Fixtures {

  val footprintResponse = (regId:String) =>
    s"""
       |{
       |  "registration-id":"$regId",
       |  "created":true,
       |  "confirmation-reference":false,
       |  "payment-reference":false,
       |  "email":{
       |    "address":"some@email.com",
       |    "type":"test",
       |    "link-sent":true,
       |    "verified":true
       |  }
       |}
     """.stripMargin

  def statusResponseFromCR(status:String = "draft", rID:String = "5") =
    s"""
       |{
       |    "registrationID" : "${rID}",
       |    "status" : "${status}",
       |    "accountingDetails" : {
       |        "accountingDateStatus" : "NOT_PLANNING_TO_YET"
       |    },
       |    "accountsPreparation" : {
       |        "businessEndDateChoice" : "HMRC_DEFINED"
       |    },
       |        "verifiedEmail" : {
       |        "address" : "user@test.com",
       |        "type" : "GG",
       |        "link-sent" : true,
       |        "verified" : true,
       |        "return-link-email-sent" : false
       |    }
       |}
     """.stripMargin
}
