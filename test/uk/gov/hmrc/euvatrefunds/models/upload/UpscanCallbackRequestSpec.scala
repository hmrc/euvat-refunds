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

import java.time.Instant

class UpscanCallbackRequestSpec extends AnyWordSpec with Matchers:

  "UpscanCallbackRequest" should {

    "read a READY callback" in {
      val json = Json.obj(
        "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "downloadUrl" -> "https://example.com/download",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.pdf",
          "fileMimeType"    -> "application/pdf",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          "size"            -> 987L
        )
      )

      json.as[UpscanCallbackRequest] shouldBe
        UpscanCallbackRequest.Ready(
          reference   = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          downloadUrl = "https://example.com/download",
          uploadDetails = UploadDetails(
            fileName        = "test.pdf",
            fileMimeType    = "application/pdf",
            uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
            checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
            size            = 987L
          )
        )
    }

    "read a FAILED callback" in {
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "FAILED",
        "failureDetails" -> Json.obj(
          "failureReason" -> "REJECTED",
          "message"       -> "MIME type is not allowed"
        )
      )

      json.as[UpscanCallbackRequest] shouldBe
        UpscanCallbackRequest.Failed(
          reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
          failureDetails = FailureDetails(
            failureReason = FailureReason.Rejected,
            message       = "MIME type is not allowed"
          )
        )
    }

    "reject an unsupported fileStatus" in {
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "PROCESSING"
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }

    "reject a callback when fileStatus is missing" in {
      val json = Json.obj(
        "reference" -> "11370e18-6e24-453e-b45a-76d3e32ea33d"
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }

    "reject a READY callback when reference is missing" in {
      val json = Json.obj(
        "downloadUrl" -> "https://example.com/download",
        "fileStatus"  -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.pdf",
          "fileMimeType"    -> "application/pdf",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "checksum",
          "size"            -> 987L
        )
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }

    "reject a READY callback when downloadUrl is missing" in {
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "READY",
        "uploadDetails" -> Json.obj(
          "fileName"        -> "test.pdf",
          "fileMimeType"    -> "application/pdf",
          "uploadTimestamp" -> "2018-04-24T09:30:00Z",
          "checksum"        -> "checksum",
          "size"            -> 987L
        )
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }

    "reject a READY callback when uploadDetails is missing" in {
      val json = Json.obj(
        "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "downloadUrl" -> "https://example.com/download",
        "fileStatus"  -> "READY"
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }

    "reject a FAILED callback when failureDetails is missing" in {
      val json = Json.obj(
        "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
        "fileStatus" -> "FAILED"
      )

      json.validate[UpscanCallbackRequest].isError shouldBe true
    }
  }
