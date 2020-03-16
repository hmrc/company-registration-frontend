
package www.takeovers

import java.util.UUID

import fixtures.Fixtures
import forms.takeovers.WhoAgreedTakeoverForm._
import itutil.servicestubs.TakeoverStub
import itutil.{IntegrationSpecBase, LoginStub, RequestsFinder}
import models.{NewAddress, TakeoverDetails}
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

class WhoAgreedTakeoverControllerISpec extends IntegrationSpecBase with LoginStub with MockitoSugar with RequestsFinder with TakeoverStub with Fixtures {

  val userId: String = "testUserId"
  val testRegId: String = "testRegId"
  val testBusinessName: String = "test name"
  val testBusinessAddress: NewAddress = NewAddress("testLine1", "testLine2", None, None, Some("Z11 11Z"), Some("testCountry"))
  val testPreviousOwnersName: String = "test owners name"
  val testTakeoverDetails: TakeoverDetails = TakeoverDetails(
    replacingAnotherBusiness = true,
    businessName = Some(testBusinessName),
    businessTakeoverAddress = Some(testBusinessAddress)
  )
  lazy val csrfToken: String = UUID.randomUUID().toString
  lazy val sessionCookie: String = getSessionCookie(Map("csrfToken" -> csrfToken), userId)

  "show" should {
    "display the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe OK
    }
    "display and prepop the page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails.copy(previousOwnersName = Some(testPreviousOwnersName))))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.show().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .get())

      res.status shouldBe OK
      Jsoup.parse(res.body).getElementById("whoAgreedTakeover").attr("value") shouldBe testPreviousOwnersName
    }
  }

  "submit" should {
    "update the takeover block and redirect to previous owners address page" in {
      stubAuthorisation()
      stubKeystore(SessionId, testRegId)
      setupFeatures(takeovers = true)
      stubGet(s"/company-registration/corporation-tax-registration/$testRegId/corporation-tax-registration", 200, statusResponseFromCR())
      stubGetTakeoverDetails(testRegId, OK, Some(testTakeoverDetails))
      stubPutTakeoverDetails(testRegId, OK, testTakeoverDetails.copy(previousOwnersName = Some(testPreviousOwnersName)))

      val res: WSResponse = await(buildClient(controllers.takeovers.routes.WhoAgreedTakeoverController.submit().url)
        .withHeaders(HeaderNames.COOKIE -> sessionCookie, "Csrf-Token" -> "nocheck")
        .post(Map(whoAgreedTakeoverKey -> Seq(testPreviousOwnersName)))
      )

      res.status shouldBe SEE_OTHER
      res.redirectLocation should contain(controllers.takeovers.routes.PreviousOwnersAddressController.show().url)
    }
  }
}
