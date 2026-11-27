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
import play.api.libs.json.{JsError, JsString, Json}

class FailureReasonSpec extends AnyWordSpec with Matchers:

  "FailureReason" should {

    "read QUARANTINE" in {
      JsString("QUARANTINE").as[FailureReason] shouldBe FailureReason.Quarantine
    }

    "read REJECTED" in {
      JsString("REJECTED").as[FailureReason] shouldBe FailureReason.Rejected
    }

    "read UNKNOWN" in {
      JsString("UNKNOWN").as[FailureReason] shouldBe FailureReason.Unknown
    }

    "write Quarantine as QUARANTINE" in {
      Json.toJson[FailureReason](FailureReason.Quarantine) shouldBe JsString("QUARANTINE")
    }

    "write Rejected as REJECTED" in {
      Json.toJson[FailureReason](FailureReason.Rejected) shouldBe JsString("REJECTED")
    }

    "write Unknown as UNKNOWN" in {
      Json.toJson[FailureReason](FailureReason.Unknown) shouldBe JsString("UNKNOWN")
    }

    "reject an unsupported failure reason" in {
      JsString("UNSUPPORTED").validate[FailureReason] shouldBe a[JsError]
    }
  }
