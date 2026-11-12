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

  private val expectedRecord = ValidationStatus.AwaitingUpload(
    applicationId     = applicationId,
    reference         = "ref-new",
    uploadUrl         = "https://upscan.example/upload",
    initiateTimestamp = now,
    fields            = Map("key" -> "ref-new"),
    createdAt         = now,
    updatedAt         = now
  )

  private val expectedResponse = CreateUploadTrackingResponse(
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

    "initiate an upload and store it when the application has no upload" in {
      applicationExists()
      when(repository.get(applicationId)).thenReturn(Future.successful(None))
      when(upscan.initiate(any())(any())).thenReturn(Future.successful(upscanResponse))
      when(repository.replaceUpload(any(), any())).thenReturn(Future.successful(true))

      service.createUploadTracking(applicationId).futureValue shouldBe Right(expectedResponse)

      verify(upscan).initiate(mockitoEq(expectedUpscanRequest))(any())
      verify(repository).replaceUpload(mockitoEq(expectedRecord), mockitoEq(None))
    }

    "replace the existing upload when the application already has one" in {
      val existing = expectedRecord.copy(reference = "ref-old", fields = Map("key" -> "ref-old"))

      applicationExists()
      when(repository.get(applicationId)).thenReturn(Future.successful(Some(existing)))
      when(upscan.initiate(any())(any())).thenReturn(Future.successful(upscanResponse))
      when(repository.replaceUpload(any(), any())).thenReturn(Future.successful(true))

      service.createUploadTracking(applicationId).futureValue shouldBe Right(expectedResponse)

      verify(repository).replaceUpload(mockitoEq(expectedRecord), mockitoEq(Some("ref-old")))
    }

    "return the stored upload when another request stored one first" in {
      val storedByOther = expectedRecord.copy(reference = "ref-other", fields = Map("key" -> "ref-other"))

      applicationExists()
      when(repository.get(applicationId))
        .thenReturn(Future.successful(None), Future.successful(Some(storedByOther)))
      when(upscan.initiate(any())(any())).thenReturn(Future.successful(upscanResponse))
      when(repository.replaceUpload(any(), any())).thenReturn(Future.successful(false))

      service.createUploadTracking(applicationId).futureValue shouldBe Right(
        CreateUploadTrackingResponse("ref-other", "https://upscan.example/upload", Map("key" -> "ref-other"))
      )
    }

    "fail and store nothing when upscan fails" in {
      applicationExists()
      when(repository.get(applicationId)).thenReturn(Future.successful(None))
      when(upscan.initiate(any())(any())).thenReturn(Future.failed(UpstreamErrorResponse("Upscan unavailable", 503)))

      service.createUploadTracking(applicationId).failed.futureValue shouldBe an[UpstreamErrorResponse]

      verify(repository, never()).replaceUpload(any(), any())
    }
  }
}
