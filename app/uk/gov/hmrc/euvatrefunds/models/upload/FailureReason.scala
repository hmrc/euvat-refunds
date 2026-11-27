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

import play.api.libs.json.*

sealed trait FailureReason

object FailureReason:

  case object Quarantine extends FailureReason
  case object Rejected   extends FailureReason
  case object Unknown    extends FailureReason

  given Format[FailureReason] = new Format[FailureReason]:

    override def writes(reason: FailureReason): JsValue =
      JsString(
        reason match
          case Quarantine => "QUARANTINE"
          case Rejected   => "REJECTED"
          case Unknown    => "UNKNOWN"
      )

    override def reads(json: JsValue): JsResult[FailureReason] =
      json.validate[String].flatMap {
        case "QUARANTINE" => JsSuccess(Quarantine)
        case "REJECTED"   => JsSuccess(Rejected)
        case "UNKNOWN"    => JsSuccess(Unknown)
        case other        => JsError(s"Unsupported Upscan failure reason: $other")
      }
