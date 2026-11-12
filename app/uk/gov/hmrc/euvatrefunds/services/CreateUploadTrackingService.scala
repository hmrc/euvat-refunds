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

package uk.gov.hmrc.euvatrefunds.services

import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.connectors.UpscanInitiateConnector
import uk.gov.hmrc.euvatrefunds.models.requests.UpscanInitiateRequest
import uk.gov.hmrc.euvatrefunds.models.responses.{CreateUploadTrackingResponse, UpscanInitiateResponse}
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus
import uk.gov.hmrc.euvatrefunds.repositories.ValidationStatusRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait CreateUploadTrackingError

object CreateUploadTrackingError:
  case object ApplicationNotFound extends CreateUploadTrackingError

@Singleton
class CreateUploadTrackingService @Inject() (
  candeService: EuVatCandeService,
  upscan: UpscanInitiateConnector,
  repository: ValidationStatusRepository,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext):

  def createUploadTracking(
    applicationId: Long
  )(implicit hc: HeaderCarrier): Future[Either[CreateUploadTrackingError, CreateUploadTrackingResponse]] =
    applicationExists(applicationId).flatMap {
      case false => Future.successful(Left(CreateUploadTrackingError.ApplicationNotFound))
      case true  => startUpload(applicationId).map(Right(_))
    }

  // assumes an unknown application comes back as a 404 from rds-cande-proxy
  private def applicationExists(applicationId: Long)(implicit hc: HeaderCarrier): Future[Boolean] =
    candeService
      .getApplicationDetails(applicationId)
      .map(_ => true)
      .recover { case UpstreamErrorResponse.WithStatusCode(404) => false }

  private def startUpload(applicationId: Long)(implicit hc: HeaderCarrier): Future[CreateUploadTrackingResponse] =
    for
      existing <- repository.get(applicationId)
      initiatedAt = clock.instant()
      initiated <- upscan.initiate(initiateRequest(applicationId))
      status = awaitingUpload(applicationId, initiated, initiatedAt)
      stored   <- repository.replaceUpload(status, existing.map(_.reference))
      response <- if stored then Future.successful(CreateUploadTrackingResponse.from(status)) else uploadStoredByAnotherRequest(applicationId, status)
    yield response

  // another request replaced the record first, so return whichever upload is now stored
  private def uploadStoredByAnotherRequest(applicationId: Long, ours: ValidationStatus.AwaitingUpload): Future[CreateUploadTrackingResponse] =
    repository.get(applicationId).map {
      case Some(stored: ValidationStatus.AwaitingUpload) => CreateUploadTrackingResponse.from(stored)
      case _                                             => CreateUploadTrackingResponse.from(ours)
    }

  private def initiateRequest(applicationId: Long): UpscanInitiateRequest =
    UpscanInitiateRequest(
      callbackUrl     = s"${appConfig.upscanCallbackBaseUrl}/$applicationId/upscan-callback",
      successRedirect = appConfig.upscanSuccessRedirect,
      errorRedirect   = appConfig.upscanErrorRedirect,
      minimumFileSize = appConfig.upscanMinimumFileSize,
      maximumFileSize = appConfig.upscanMaximumFileSize
    )

  private def awaitingUpload(applicationId: Long, initiated: UpscanInitiateResponse, initiatedAt: Instant): ValidationStatus.AwaitingUpload =
    ValidationStatus.AwaitingUpload(
      applicationId     = applicationId,
      reference         = initiated.reference,
      uploadUrl         = initiated.uploadRequest.href,
      initiateTimestamp = initiatedAt,
      fields            = initiated.uploadRequest.fields,
      createdAt         = initiatedAt,
      updatedAt         = initiatedAt
    )
