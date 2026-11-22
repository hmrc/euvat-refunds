/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.euvatrefunds.connectors.upscan

import play.api.libs.json.Json
import play.api.libs.ws.writeableOf_JsValue
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.upscan.{PreparedUpload, UpscanFileReference, UpscanInitiateRequest, UpscanInitiateResponse}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanInitiateConnector @Inject() (
  httpClient: HttpClientV2,
  appConfig: AppConfig
)(using ExecutionContext):

  private val baseUrl: String = appConfig.baseUrl("upscan-initiate")

  private val headers = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  def initiate(
    redirectOnSuccess: Option[String],
    redirectOnError: Option[String],
    applicationId: Long
  )(using HeaderCarrier): Future[UpscanInitiateResponse] =
    val request = UpscanInitiateRequest(
      callbackUrl     = s"${appConfig.upscanCallbackBaseUrl}/$applicationId/upscan-callback",
      successRedirect = redirectOnSuccess,
      errorRedirect   = redirectOnError,
      minimumFileSize = Some(appConfig.upscanMinimumFileSize),
      maximumFileSize = Some(appConfig.upscanMaximumFileSize)
    )

    for {
      response <- httpClient
                    .post(url"$baseUrl/upscan/v2/initiate")
                    .withBody(Json.toJson(request))
                    .setHeader(headers.toSeq*)
                    .execute[PreparedUpload]
      fileReference = UpscanFileReference(response.reference.value)
      postTarget = response.uploadRequest.href
      formFields = response.uploadRequest.fields
    } yield UpscanInitiateResponse(fileReference, postTarget, formFields)
