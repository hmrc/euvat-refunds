package uk.gov.hmrc.euvatrefunds.models.responses

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class SadReferenceCountResponseSpec extends AnyWordSpec with Matchers {

  "SadReferenceCountResponse JSON format" should {

    "serialize to JSON correctly" in {
      Json.toJson(SadReferenceCountResponse(duplicateCount = 3)) shouldBe Json.obj("duplicateCount" -> 3)
    }

    "deserialize from JSON correctly" in {
      Json.obj("duplicateCount" -> 3).as[SadReferenceCountResponse] shouldBe SadReferenceCountResponse(duplicateCount = 3)
    }
  }
}
