package uk.gov.hmrc.euvatrefunds.models.requests

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class SadReferenceCountRequestSpec extends AnyWordSpec with Matchers {
  "SadReferenceCountRequest JSON format" should {

    "serialize to JSON correctly" in {
      val request = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      val json = Json.toJson(request)

      (json \ "applicationId").as[Long]        shouldBe 133L
      (json \ "itemNumber").as[Int]            shouldBe 4
      (json \ "sadReferenceNumber").as[String] shouldBe "500000881"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicationId"      -> 133,
        "itemNumber"         -> 4,
        "sadReferenceNumber" -> "500000881"
      )

      val result = json.as[SadReferenceCountRequest]

      result.applicationId      shouldBe 133L
      result.itemNumber         shouldBe 4
      result.sadReferenceNumber shouldBe "500000881"
    }

    "support round-trip JSON conversion" in {
      val original = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      val json = Json.toJson(original)
      val parsed = json.as[SadReferenceCountRequest]

      parsed shouldBe original
    }
  }
}
