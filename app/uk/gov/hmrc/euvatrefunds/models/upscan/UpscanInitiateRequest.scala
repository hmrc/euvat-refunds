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

package uk.gov.hmrc.euvatrefunds.models.upscan

import play.api.libs.json.{Format, Json, Reads}

case class UpscanInitiateRequest(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String] = None,
  minimumFileSize: Option[Int] = None,
  maximumFileSize: Option[Int] = Some(2097152)
)

object UpscanInitiateRequest:
  given Format[UpscanInitiateRequest] = Json.format[UpscanInitiateRequest]

case class UploadForm(
  href: String,
  fields: Map[String, String]
)

case class Reference(value: String) extends AnyVal

object Reference:
  given Reads[Reference] = Reads.StringReads.map(Reference(_))

case class PreparedUpload(
  reference: Reference,
  uploadRequest: UploadForm
)

object PreparedUpload:
  given Reads[UploadForm] = Json.reads[UploadForm]
  given Reads[PreparedUpload] = Json.reads[PreparedUpload]
