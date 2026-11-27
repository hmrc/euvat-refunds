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

import play.api.libs.json.{JsError, Json, Reads}

sealed trait UpscanCallbackRequest:
  def reference: String

object UpscanCallbackRequest:

  final case class Ready(
    reference: String,
    downloadUrl: String,
    uploadDetails: UploadDetails
  ) extends UpscanCallbackRequest

  final case class Failed(
    reference: String,
    failureDetails: FailureDetails
  ) extends UpscanCallbackRequest

  private val readyReads: Reads[Ready] =
    Json.reads[Ready]

  private val failedReads: Reads[Failed] =
    Json.reads[Failed]

  given reads: Reads[UpscanCallbackRequest] = json =>
    (json \ "fileStatus").validate[String].flatMap {
      case "READY" =>
        readyReads.reads(json)

      case "FAILED" =>
        failedReads.reads(json)

      case other =>
        JsError(s"Unsupported fileStatus: $other")
    }
