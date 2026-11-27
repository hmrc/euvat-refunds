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

class UploadDetailsSpec extends AnyWordSpec with Matchers:

  "UploadDetails" should {

    "read from JSON" in {
      val json = Json.obj(
        "fileName"        -> "test.pdf",
        "fileMimeType"    -> "application/pdf",
        "uploadTimestamp" -> "2018-04-24T09:30:00Z",
        "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "size"            -> 987L
      )

      json.as[UploadDetails] shouldBe UploadDetails(
        fileName        = "test.pdf",
        fileMimeType    = "application/pdf",
        uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
        checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        size            = 987L
      )
    }

    "write to JSON" in {
      val uploadDetails = UploadDetails(
        fileName        = "test.pdf",
        fileMimeType    = "application/pdf",
        uploadTimestamp = Instant.parse("2018-04-24T09:30:00Z"),
        checksum        = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        size            = 987L
      )

      Json.toJson(uploadDetails) shouldBe Json.obj(
        "fileName"        -> "test.pdf",
        "fileMimeType"    -> "application/pdf",
        "uploadTimestamp" -> "2018-04-24T09:30:00Z",
        "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        "size"            -> 987L
      )
    }

    "support a file size larger than Int.MaxValue" in {
      val largeFileSize = Int.MaxValue.toLong + 1

      val json = Json.obj(
        "fileName"        -> "large-file.zip",
        "fileMimeType"    -> "application/zip",
        "uploadTimestamp" -> "2018-04-24T09:30:00Z",
        "checksum"        -> "checksum",
        "size"            -> largeFileSize
      )

      json.as[UploadDetails].size shouldBe largeFileSize
    }

    "fail to read JSON when fileName is missing" in {
      val json = Json.obj(
        "fileMimeType"    -> "application/pdf",
        "uploadTimestamp" -> "2018-04-24T09:30:00Z",
        "checksum"        -> "checksum",
        "size"            -> 987L
      )

      json.validate[UploadDetails].isError shouldBe true
    }

    "fail to read JSON when uploadTimestamp is invalid" in {
      val json = Json.obj(
        "fileName"        -> "test.pdf",
        "fileMimeType"    -> "application/pdf",
        "uploadTimestamp" -> "not-a-timestamp",
        "checksum"        -> "checksum",
        "size"            -> 987L
      )

      json.validate[UploadDetails].isError shouldBe true
    }

    "fail to read JSON when size is missing" in {
      val json = Json.obj(
        "fileName"        -> "test.pdf",
        "fileMimeType"    -> "application/pdf",
        "uploadTimestamp" -> "2018-04-24T09:30:00Z",
        "checksum"        -> "checksum"
      )

      json.validate[UploadDetails].isError shouldBe true
    }
  }
