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

package uk.gov.hmrc.euvatrefunds.models.upload

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class FailureDetailsSpec extends AnyWordSpec with Matchers:

  "FailureDetails" should {

    "read from JSON" in {
      val json = Json.obj(
        "failureReason" -> "REJECTED",
        "message"       -> "MIME type is not allowed"
      )

      json.as[FailureDetails] shouldBe FailureDetails(
        failureReason = FailureReason.Rejected,
        message       = "MIME type is not allowed"
      )
    }

    "write to JSON" in {
      val failureDetails = FailureDetails(
        failureReason = FailureReason.Quarantine,
        message       = "This file has a virus"
      )

      Json.toJson(failureDetails) shouldBe Json.obj(
        "failureReason" -> "QUARANTINE",
        "message"       -> "This file has a virus"
      )
    }

    "fail to read JSON when failureReason is unsupported" in {
      val json = Json.obj(
        "failureReason" -> "INVALID",
        "message"       -> "Something went wrong"
      )

      json.validate[FailureDetails].isError shouldBe true
    }

    "fail to read JSON when failureReason is missing" in {
      val json = Json.obj(
        "message" -> "Something went wrong"
      )

      json.validate[FailureDetails].isError shouldBe true
    }

    "fail to read JSON when message is missing" in {
      val json = Json.obj(
        "failureReason" -> "UNKNOWN"
      )

      json.validate[FailureDetails].isError shouldBe true
    }
  }
