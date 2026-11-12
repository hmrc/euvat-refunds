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
import play.api.libs.json.{JsError, JsSuccess, Json}

class UpscanInitiateResponseSpec extends AnyWordSpec with Matchers {

  "UpscanInitiateResponse reads" should {
    "read a valid upscan response" in {
      val json = Json.parse("""{"reference":"ref-1","uploadRequest":{"href":"https://upscan.example","fields":{"key":"ref-1"}}}""")

      json.validate[UpscanInitiateResponse] shouldBe JsSuccess(
        UpscanInitiateResponse("ref-1", UploadRequest("https://upscan.example", Map("key" -> "ref-1")))
      )
    }

    "fail when the reference is missing" in {
      Json.parse("""{"uploadRequest":{"href":"https://upscan.example","fields":{}}}""").validate[UpscanInitiateResponse] shouldBe a[JsError]
    }

    "fail when the upload request is missing" in {
      Json.parse("""{"reference":"ref-1"}""").validate[UpscanInitiateResponse] shouldBe a[JsError]
    }

    "fail when the upload request has no href" in {
      Json.parse("""{"reference":"ref-1","uploadRequest":{"fields":{}}}""").validate[UpscanInitiateResponse] shouldBe a[JsError]
    }

    "fail when the fields are missing" in {
      Json.parse("""{"reference":"ref-1","uploadRequest":{"href":"https://upscan.example"}}""").validate[UpscanInitiateResponse] shouldBe a[JsError]
    }
  }
}
