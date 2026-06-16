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

package uk.gov.hmrc.euvatrefunds.models.responses

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.*

import java.time.LocalDateTime

class LatestApplicationResponseSpec extends AnyWordSpec with Matchers {

  private val sampleApplication = LatestApplication(
    applicationId        = 133,
    refundingCountryCode = "LV",
    periodStartDate      = LocalDateTime.of(2025, 2, 1, 0, 0),
    periodEndDate        = LocalDateTime.of(2025, 5, 31, 23, 59),
    applicationNumber    = "GB0000000000000133",
    applicationStatus    = "D",
    submissionStatus     = "S",
    applicationVersion   = LocalDateTime.of(2025, 2, 11, 10, 38)
  )

  private val sampleResponse = LatestApplicationResponse(
    applications     = List(sampleApplication),
    totalApplication = 1
  )

  "LatestApplicationResponse JSON format" should {

    "serialize to JSON correctly" in {
      val json = Json.toJson(sampleResponse)

      (json \ "totalApplication").as[Int]                             shouldBe 1
      (json \ "applications" \ 0 \ "applicationId").as[Long]          shouldBe 133
      (json \ "applications" \ 0 \ "refundingCountryCode").as[String] shouldBe "LV"
      (json \ "applications" \ 0 \ "applicationStatus").as[String]    shouldBe "D"
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse(
        """
          |{
          |  "applications": [{
          |    "applicationId": 133,
          |    "refundingCountryCode": "LV",
          |    "periodStartDate": "2025-02-01T00:00:00",
          |    "periodEndDate": "2025-05-31T23:59:00",
          |    "applicationNumber": "GB0000000000000133",
          |    "applicationStatus": "D",
          |    "submissionStatus": "S",
          |    "applicationVersion": "2025-02-11T10:38:00"
          |  }],
          |  "totalApplication": 1
          |}
          |""".stripMargin
      )

      val model = json.as[LatestApplicationResponse]

      model.totalApplication                       shouldBe 1
      model.applications.head.applicationId        shouldBe 133
      model.applications.head.refundingCountryCode shouldBe "LV"
    }

    "round-trip JSON (serialize then deserialize)" in {
      val json = Json.toJson(sampleResponse)
      val parsed = json.as[LatestApplicationResponse]

      parsed shouldBe sampleResponse
    }
  }
}
