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
import play.api.libs.json.{JsError, JsNumber, JsString, JsSuccess, Json}

class FileStatusSpec extends AnyWordSpec with Matchers {

  "FileStatus format" should {

    "write AwaitingUpload as AWAITING_UPLOAD" in {
      Json.toJson[FileStatus](FileStatus.AwaitingUpload) shouldBe
        JsString("AWAITING_UPLOAD")
    }

    "write Ready as READY" in {
      Json.toJson[FileStatus](FileStatus.Ready) shouldBe
        JsString("READY")
    }

    "write Failed as FAILED" in {
      Json.toJson[FileStatus](FileStatus.Failed) shouldBe
        JsString("FAILED")
    }

    "read AWAITING_UPLOAD as AwaitingUpload" in {
      JsString("AWAITING_UPLOAD").validate[FileStatus] shouldBe
        JsSuccess(FileStatus.AwaitingUpload)
    }

    "read READY as Ready" in {
      JsString("READY").validate[FileStatus] shouldBe
        JsSuccess(FileStatus.Ready)
    }

    "read FAILED as Failed" in {
      JsString("FAILED").validate[FileStatus] shouldBe
        JsSuccess(FileStatus.Failed)
    }

    "fail to read an unknown status" in {
      JsString("NOT_A_STATUS").validate[FileStatus] shouldBe a[JsError]
    }

    "fail to read a value that is not a string" in {
      JsNumber(1).validate[FileStatus] shouldBe a[JsError]
    }
  }
}
