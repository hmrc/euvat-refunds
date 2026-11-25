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

import com.typesafe.config.ConfigFactory
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as mockitoEq}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.connectors.UpscanInitiateConnector
import uk.gov.hmrc.euvatrefunds.models.requests.UpscanInitiateRequest
import uk.gov.hmrc.euvatrefunds.models.responses.{ApplicationDetailsResponse, CreateUploadTrackingResponse, UploadRequest, UpscanInitiateResponse}
import uk.gov.hmrc.euvatrefunds.models.upload.ValidationStatus
import uk.gov.hmrc.euvatrefunds.repositories.ValidationStatusRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

class CreateUploadTrackingServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val candeService = mock[EuVatCandeService]
  private val upscan = mock[UpscanInitiateConnector]
  private val repository = mock[ValidationStatusRepository]

  private val appConfig = new AppConfig(
    Configuration(
      ConfigFactory.parseString(
        """
          |appName = euvat-refunds
          |upscan {
          |  callbackBaseUrl = "http://localhost:18502/euvat-refunds"
          |  successRedirect = "http://localhost:18501/supporting-documents/your-file"
          |  errorRedirect   = "http://localhost:18501/supporting-documents/upload-file"
          |  minimumFileSize = 1
          |  maximumFileSize = 5242880
          |}
          |""".stripMargin
      )
    )
  )

  private val now = Instant.parse("2026-11-12T10:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private val service = new CreateUploadTrackingService(candeService, upscan, repository, appConfig, clock)

  private val applicationId = 133L

  private val applicationDetails = Json
    .parse("""{"applicationDetails":[{"applicationId":133,"updateSeqNumber":1}]}""")
    .as[ApplicationDetailsResponse]

  private val expectedUpscanRequest = UpscanInitiateRequest(
    callbackUrl     = "http://localhost:18502/euvat-refunds/133/upscan-callback",
    successRedirect = "http://localhost:18501/supporting-documents/your-file",
    errorRedirect   = "http://localhost:18501/supporting-documents/upload-file",
    minimumFileSize = 1,
    maximumFileSize = 5242880
  )

  private val upscanResponse = UpscanInitiateResponse(
    reference     = "ref-new",
    uploadRequest = UploadRequest(href = "https://upscan.example/upload", fields = Map("key" -> "ref-new"))
  )

  // the upload id is a random UUID, so tests take it from the record that was stored
  private def expectedRecord(uploadId: String) = ValidationStatus.AwaitingUpload(
    uploadId          = uploadId,
    applicationId     = applicationId,
    reference         = "ref-new",
    uploadUrl         = "https://upscan.example/upload",
    initiateTimestamp = now,
    fields            = Map("key" -> "ref-new"),
    createdAt         = now,
    updatedAt         = now
  )

  private def expectedResponse(uploadId: String) = CreateUploadTrackingResponse(
    uploadId  = uploadId,
    reference = "ref-new",
    uploadUrl = "https://upscan.example/upload",
    fields    = Map("key" -> "ref-new")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(candeService)
    reset(upscan)
    reset(repository)
  }

  private def applicationExists(): Unit =
    when(candeService.getApplicationDetails(any[Long]())(any()))
      .thenReturn(Future.successful(applicationDetails))

  private def uploadStartsWith(inProgress: Option[ValidationStatus.AwaitingUpload]): Unit = {
    applicationExists()
    when(repository.findAwaitingUpload(applicationId)).thenReturn(Future.successful(inProgress))
    when(upscan.initiate(any())(any())).thenReturn(Future.successful(upscanResponse))
    when(repository.replaceAwaitingUpload(any(), any())).thenReturn(Future.successful(true))
  }

  private def storedUploads(): Seq[ValidationStatus.AwaitingUpload] = {
    val captor = ArgumentCaptor.forClass(classOf[ValidationStatus.AwaitingUpload])
    verify(repository, atLeastOnce()).replaceAwaitingUpload(captor.capture(), any())
    captor.getAllValues.asScala.toSeq
  }

  "CreateUploadTrackingService.createUploadTracking" should {

    "return ApplicationNotFound and not call upscan when the application does not exist" in {
      when(candeService.getApplicationDetails(any[Long]())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("Not found", 404)))

      service.createUploadTracking(applicationId).futureValue shouldBe Left(CreateUploadTrackingError.ApplicationNotFound)

      verifyNoInteractions(upscan)
      verifyNoInteractions(repository)
    }

    "fail and not call upscan when the application check fails with another error" in {
      when(candeService.getApplicationDetails(any[Long]())(any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("Server error", 500)))

      service.createUploadTracking(applicationId).failed.futureValue shouldBe an[UpstreamErrorResponse]

      verifyNoInteractions(upscan)
    }

    "initiate an upload and store it as a new record when nothing is in progress" in {
      uploadStartsWith(inProgress = None)

      val result = service.createUploadTracking(applicationId).futureValue

      verify(upscan).initiate(mockitoEq(expectedUpscanRequest))(any())
      verify(repository).replaceAwaitingUpload(any(), mockitoEq(None))
      val stored = storedUploads().head
      stored.uploadId should not be empty
      stored        shouldBe expectedRecord(stored.uploadId)
      result        shouldBe Right(expectedResponse(stored.uploadId))
    }

    "replace the upload in progress for the application" in {
      val inProgress = expectedRecord("upload-old").copy(reference = "ref-old", fields = Map("key" -> "ref-old"))
      uploadStartsWith(inProgress = Some(inProgress))

      service.createUploadTracking(applicationId).futureValue

      verify(repository).replaceAwaitingUpload(any(), mockitoEq(Some("ref-old")))
      storedUploads().head.uploadId should not be "upload-old"
    }

    "create a new upload id every time an upload is initiated" in {
      uploadStartsWith(inProgress = None)

      service.createUploadTracking(applicationId).futureValue
      service.createUploadTracking(applicationId).futureValue

      val uploadIds = storedUploads().map(_.uploadId)
      uploadIds          should have size 2
      uploadIds.distinct should have size 2
    }

    "use the redirects passed in instead of the configured ones" in {
      uploadStartsWith(inProgress = None)

      service.createUploadTracking(applicationId, Some("http://success"), Some("http://error")).futureValue

      verify(upscan).initiate(
        mockitoEq(expectedUpscanRequest.copy(successRedirect = "http://success", errorRedirect = "http://error"))
      )(any())
    }

    "return the upload in progress when another request stored one first" in {
      val storedByOther = expectedRecord("upload-other").copy(reference = "ref-other", fields = Map("key" -> "ref-other"))

      applicationExists()
      when(repository.findAwaitingUpload(applicationId))
        .thenReturn(Future.successful(None), Future.successful(Some(storedByOther)))
      when(upscan.initiate(any())(any())).thenReturn(Future.successful(upscanResponse))
      when(repository.replaceAwaitingUpload(any(), any())).thenReturn(Future.successful(false))

      service.createUploadTracking(applicationId).futureValue shouldBe Right(
        CreateUploadTrackingResponse("upload-other", "ref-other", "https://upscan.example/upload", Map("key" -> "ref-other"))
      )
    }

    "fail and store nothing when upscan fails" in {
      applicationExists()
      when(repository.findAwaitingUpload(applicationId)).thenReturn(Future.successful(None))
      when(upscan.initiate(any())(any())).thenReturn(Future.failed(UpstreamErrorResponse("Upscan unavailable", 503)))

      service.createUploadTracking(applicationId).failed.futureValue shouldBe an[UpstreamErrorResponse]

      verify(repository, never()).replaceAwaitingUpload(any(), any())
    }
  }
}
