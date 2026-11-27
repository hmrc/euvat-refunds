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

package uk.gov.hmrc.euvatrefunds.models.utils

import play.api.libs.json.*

def jsonStringFormat[E](allValues: IndexedSeq[E], stringValueOf: E => String): Format[E] =
  val byStringValue: Map[String, E] = allValues.iterator.map(e => stringValueOf(e) -> e).toMap
  require(
    byStringValue.size == allValues.size,
    s"duplicate string value in format: ${allValues.map(stringValueOf).mkString(", ")}"
  )
  Format(
    Reads {
      case JsString(value) =>
        byStringValue.get(value) match
          case Some(e) => JsSuccess(e)
          case None    => JsError(s"unrecognised value '$value' - expected one of ${byStringValue.keys.toList.sorted.mkString(", ")}")
      case other => JsError(s"expected a JSON string but got $other")
    },
    Writes(e => JsString(stringValueOf(e)))
  )
