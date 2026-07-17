/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.euvatrefunds.models.requests

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

import java.time.LocalDateTime

class LatestApplicationRequestSpec extends AnyWordSpec with Matchers {

  private val sampleRequest = LatestApplicationRequest(
    applicantVatRegNumber = "123456789",
    refundingCountry      = Some("LV"),
    startDate             = Some(LocalDateTime.of(2025, 2, 1, 0, 0)),
    endDate               = Some(LocalDateTime.of(2025, 5, 31, 0, 0)),
    representativeId      = Some("rep123"),
    maxNumber             = 10,
    orderBy               = None,
    sortOrder             = None,
    startAt               = None
  )

  "LatestApplicationRequest JSON format" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(sampleRequest)

      (json \ "applicantVatRegNumber").as[String] shouldBe "123456789"
      (json \ "refundingCountry").as[String]      shouldBe "LV"
      (json \ "maxNumber").as[Int]                shouldBe 10
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse(
        """
          |{
          |  "applicantVatRegNumber": "123456789",
          |  "refundingCountry": "LV",
          |  "startDate": "2025-02-01T00:00:00",
          |  "endDate": "2025-05-31T00:00:00",
          |  "representativeId": "rep123",
          |  "maxNumber": 10
          |}
          |""".stripMargin
      )

      val model = json.as[LatestApplicationRequest]

      model.applicantVatRegNumber shouldBe "123456789"
      model.refundingCountry      shouldBe Some("LV")
      model.maxNumber             shouldBe 10
      model.orderBy               shouldBe None
      model.sortOrder             shouldBe None
      model.startAt               shouldBe None
    }

    "handle missing optional fields" in {
      val json = Json.parse(
        """
          |{
          |  "applicantVatRegNumber": "123456789",
          |  "maxNumber": 10
          |}
          |""".stripMargin
      )

      val model = json.as[LatestApplicationRequest]

      model.refundingCountry shouldBe None
      model.startDate        shouldBe None
      model.endDate          shouldBe None
      model.representativeId shouldBe None
      model.orderBy          shouldBe None
      model.sortOrder        shouldBe None
      model.startAt          shouldBe None
    }

    "round-trip JSON (serialize then deserialize)" in {
      val json = Json.toJson(sampleRequest)
      val parsed = json.as[LatestApplicationRequest]

      parsed shouldBe sampleRequest
    }
  }
}
