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
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord}
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Clock, Instant}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait CreateUploadTrackingError

object CreateUploadTrackingError:
  case object ApplicationNotFound extends CreateUploadTrackingError

@Singleton
class CreateUploadTrackingService @Inject() (
  candeService: EuVatCandeService,
  upscan: UpscanInitiateConnector,
  repository: UploadRepository,
  appConfig: AppConfig,
  clock: Clock
)(implicit ec: ExecutionContext):

  def createUploadTracking(
    applicationId: Long,
    successRedirect: Option[String] = None,
    errorRedirect: Option[String] = None
  )(implicit
    hc: HeaderCarrier
  ): Future[
    Either[
      CreateUploadTrackingError,
      CreateUploadTrackingResponse
    ]
  ] =
    applicationExists(applicationId).flatMap {
      case false =>
        Future.successful(
          Left(CreateUploadTrackingError.ApplicationNotFound)
        )

      case true =>
        startUpload(
          applicationId,
          successRedirect,
          errorRedirect
        ).map(Right(_))
    }

  private def applicationExists(
    applicationId: Long
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    candeService
      .getApplicationDetails(applicationId)
      .map(_ => true)
      .recover { case UpstreamErrorResponse.WithStatusCode(404) =>
        false
      }

  private def startUpload(
    applicationId: Long,
    successRedirect: Option[String],
    errorRedirect: Option[String]
  )(implicit hc: HeaderCarrier): Future[CreateUploadTrackingResponse] =
    for
      inProgress <-
        repository.findAwaitingUpload(applicationId)

      initiatedAt =
        clock.instant()

      upscanResponse <-
        upscan.initiate(
          initiateRequest(
            applicationId,
            successRedirect,
            errorRedirect
          )
        )

      newUpload =
        awaitingUpload(
          applicationId,
          upscanResponse,
          initiatedAt
        )

      isStored <-
        repository.replaceAwaitingUpload(
          newUpload,
          inProgress.map(_.reference)
        )

      response <-
        if isStored then
          Future.successful(
            CreateUploadTrackingResponse.from(newUpload)
          )
        else
          repository
            .findAwaitingUpload(applicationId)
            .map { currentUpload =>
              CreateUploadTrackingResponse.from(
                currentUpload.getOrElse(newUpload)
              )
            }
    yield response

  private def initiateRequest(
    applicationId: Long,
    successRedirect: Option[String],
    errorRedirect: Option[String]
  ): UpscanInitiateRequest =
    UpscanInitiateRequest(
      callbackUrl     = s"${appConfig.upscanCallbackBaseUrl}/$applicationId/upscan-callback",
      successRedirect = successRedirect.getOrElse(appConfig.upscanSuccessRedirect),
      errorRedirect   = errorRedirect.getOrElse(appConfig.upscanErrorRedirect),
      minimumFileSize = appConfig.upscanMinimumFileSize,
      maximumFileSize = appConfig.upscanMaximumFileSize
    )

  private def awaitingUpload(
    applicationId: Long,
    upscanResponse: UpscanInitiateResponse,
    initiatedAt: Instant
  ): UploadRecord =
    UploadRecord(
      uploadId          = newUploadId(),
      applicationId     = applicationId,
      reference         = upscanResponse.reference,
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = upscanResponse.uploadRequest.href,
      initiateTimestamp = initiatedAt,
      fields            = upscanResponse.uploadRequest.fields,
      downloadUrl       = None,
      uploadDetails     = None,
      failureDetails    = None,
      createdAt         = initiatedAt,
      updatedAt         = initiatedAt
    )

  // Every Upscan initiation creates a new upload record with its own Mongo _id.
  private def newUploadId(): String =
    UUID.randomUUID().toString
