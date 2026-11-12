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

// one record per application: tracks the upload currently in progress
// TODO: further statuses are added in F6.4 - Update Upload Status
sealed trait ValidationStatus:
  def applicationId: Long
  def reference: String
  def fileStatus: FileStatus
  def createdAt: Instant
  def updatedAt: Instant

object ValidationStatus:

  final case class AwaitingUpload(
    applicationId: Long,
    reference: String,
    uploadUrl: String,
    initiateTimestamp: Instant,
    fields: Map[String, String],
    createdAt: Instant,
    updatedAt: Instant
  ) extends ValidationStatus:
    val fileStatus: FileStatus = FileStatus.AwaitingUpload

  // ttl only works on a bson date, so instants are stored in mongo format
  private given Format[Instant] = MongoJavatimeFormats.instantFormat

  private val awaitingUploadFormat: OFormat[AwaitingUpload] = (
    (__ \ "_id").format[Long] and
      (__ \ "reference").format[String] and
      (__ \ "uploadUrl").format[String] and
      (__ \ "initiateTimestamp").format[Instant] and
      (__ \ "fields").format[Map[String, String]] and
      (__ \ "createdAt").format[Instant] and
      (__ \ "updatedAt").format[Instant]
  )(
    AwaitingUpload.apply,
    s => (s.applicationId, s.reference, s.uploadUrl, s.initiateTimestamp, s.fields, s.createdAt, s.updatedAt)
  )

  given format: OFormat[ValidationStatus] = new OFormat[ValidationStatus]:

    override def writes(status: ValidationStatus): JsObject =
      val body = status match
        case s: AwaitingUpload => awaitingUploadFormat.writes(s)
      body + ("fileStatus" -> Json.toJson(status.fileStatus))

    override def reads(json: JsValue): JsResult[ValidationStatus] =
      (json \ "fileStatus")
        .validate[FileStatus]
        .flatMap { case FileStatus.AwaitingUpload =>
          awaitingUploadFormat.reads(json)
        }
