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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class UploadRecord(
  uploadId: String,
  applicationId: Long,
  reference: String,
  fileStatus: FileStatus,
  uploadUrl: String,
  initiateTimestamp: Instant,
  fields: Map[String, String],
  downloadUrl: Option[String],
  uploadDetails: Option[UploadDetails],
  failureDetails: Option[FailureDetails],
  createdAt: Instant,
  updatedAt: Instant
)

object UploadRecord:

  private given Format[Instant] =
    MongoJavatimeFormats.instantFormat

  val format: OFormat[UploadRecord] = (
    (__ \ "_id").format[String] and
      (__ \ "applicationId").format[Long] and
      (__ \ "reference").format[String] and
      (__ \ "fileStatus").format[FileStatus] and
      (__ \ "uploadUrl").format[String] and
      (__ \ "initiateTimestamp").format[Instant] and
      (__ \ "fields").format[Map[String, String]] and
      (__ \ "downloadUrl").formatNullable[String] and
      (__ \ "uploadDetails").formatNullable[UploadDetails] and
      (__ \ "failureDetails").formatNullable[FailureDetails] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(UploadRecord.apply, Tuple.fromProductTyped)

  given OFormat[UploadRecord] =
    format
