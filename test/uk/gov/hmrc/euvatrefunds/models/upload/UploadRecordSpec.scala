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
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}

import java.time.Instant

class UploadRecordSpec extends AnyWordSpec with Matchers {

  private val createdAt =
    Instant.parse("2026-11-26T10:00:00Z")

  private val updatedAt =
    Instant.parse("2026-11-26T10:05:00Z")

  private val uploadTimestamp =
    Instant.parse("2026-11-26T10:02:00Z")

  private val uploadDetails =
    UploadDetails(
      fileName        = "test.pdf",
      fileMimeType    = "application/pdf",
      uploadTimestamp = uploadTimestamp,
      checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      size            = 987L
    )

  private val failureDetails =
    FailureDetails(
      failureReason = FailureReason.Rejected,
      message       = "MIME type is not allowed"
    )

  private val awaitingUpload =
    UploadRecord(
      uploadId          = "upload-123",
      applicationId     = 12345L,
      reference         = "reference-123",
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = "https://example.com/upload",
      initiateTimestamp = createdAt,
      fields = Map(
        "key"    -> "reference-123",
        "policy" -> "test-policy"
      ),
      downloadUrl    = None,
      uploadDetails  = None,
      failureDetails = None,
      createdAt      = createdAt,
      updatedAt      = createdAt
    )

  "UploadRecord format" should {

    "write an awaiting upload using uploadId as the Mongo _id" in {
      val json =
        Json.toJson(awaitingUpload)

      (json \ "_id").as[String]            shouldBe "upload-123"
      (json \ "applicationId").as[Long]    shouldBe 12345L
      (json \ "reference").as[String]      shouldBe "reference-123"
      (json \ "fileStatus").as[FileStatus] shouldBe FileStatus.AwaitingUpload
      (json \ "uploadUrl").as[String]      shouldBe "https://example.com/upload"
      (json \ "fields").as[Map[String, String]] shouldBe Map(
        "key"    -> "reference-123",
        "policy" -> "test-policy"
      )
    }

    "round trip an awaiting upload" in {
      val json =
        Json.toJson(awaitingUpload)

      json.validate[UploadRecord] shouldBe
        JsSuccess(awaitingUpload)
    }

    "round trip a ready upload" in {
      val readyUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Ready,
          downloadUrl    = Some("https://example.com/download"),
          uploadDetails  = Some(uploadDetails),
          failureDetails = None,
          updatedAt      = updatedAt
        )

      val json =
        Json.toJson(readyUpload)

      json.validate[UploadRecord] shouldBe
        JsSuccess(readyUpload)
    }

    "round trip a failed upload" in {
      val failedUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Failed,
          downloadUrl    = None,
          uploadDetails  = None,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      val json =
        Json.toJson(failedUpload)

      json.validate[UploadRecord] shouldBe
        JsSuccess(failedUpload)
    }

    "write READY status and callback details for a ready upload" in {
      val readyUpload =
        awaitingUpload.copy(
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some("https://example.com/download"),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      val json =
        Json.toJson(readyUpload)

      (json \ "fileStatus").as[String] shouldBe "READY"
      (json \ "downloadUrl").as[String] shouldBe
        "https://example.com/download"

      (json \ "uploadDetails").as[UploadDetails] shouldBe
        uploadDetails
    }

    "write FAILED status and failure details for a failed upload" in {
      val failedUpload =
        awaitingUpload.copy(
          fileStatus     = FileStatus.Failed,
          failureDetails = Some(failureDetails),
          updatedAt      = updatedAt
        )

      val json =
        Json.toJson(failedUpload)

      (json \ "fileStatus").as[String] shouldBe "FAILED"

      (json \ "failureDetails").as[FailureDetails] shouldBe
        failureDetails
    }

    "preserve the original upload details after moving to Ready" in {
      val readyUpload =
        awaitingUpload.copy(
          fileStatus    = FileStatus.Ready,
          downloadUrl   = Some("https://example.com/download"),
          uploadDetails = Some(uploadDetails),
          updatedAt     = updatedAt
        )

      readyUpload.uploadId          shouldBe awaitingUpload.uploadId
      readyUpload.applicationId     shouldBe awaitingUpload.applicationId
      readyUpload.reference         shouldBe awaitingUpload.reference
      readyUpload.uploadUrl         shouldBe awaitingUpload.uploadUrl
      readyUpload.initiateTimestamp shouldBe awaitingUpload.initiateTimestamp
      readyUpload.fields            shouldBe awaitingUpload.fields
      readyUpload.createdAt         shouldBe awaitingUpload.createdAt
    }

    "read an awaiting upload when optional callback fields are absent" in {
      val json =
        Json
          .toJson(awaitingUpload)(UploadRecord.format)
          .as[JsObject] -
          "downloadUrl" -
          "uploadDetails" -
          "failureDetails"

      json.validate[UploadRecord](UploadRecord.format) shouldBe
        JsSuccess(awaitingUpload)
    }

    "fail to read an upload when uploadUrl is missing" in {
      val json =
        Json
          .toJson(awaitingUpload)
          .as[play.api.libs.json.JsObject] - "uploadUrl"

      json.validate[UploadRecord] shouldBe a[JsError]
    }

    "fail to read an upload with an unknown file status" in {
      val json =
        Json
          .toJson(awaitingUpload)
          .as[play.api.libs.json.JsObject] +
          ("fileStatus" -> Json.toJson("UNKNOWN"))

      json.validate[UploadRecord] shouldBe a[JsError]
    }

    "fail to read an upload when _id is missing" in {
      val json =
        Json
          .toJson(awaitingUpload)
          .as[play.api.libs.json.JsObject] - "_id"

      json.validate[UploadRecord] shouldBe a[JsError]
    }
  }
}
