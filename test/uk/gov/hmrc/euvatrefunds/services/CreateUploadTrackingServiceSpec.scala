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
import uk.gov.hmrc.euvatrefunds.models.upload.{FileStatus, UploadRecord}
import uk.gov.hmrc.euvatrefunds.repositories.UploadRepository
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

class CreateUploadTrackingServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach:

  implicit private val ec: ExecutionContext =
    ExecutionContext.global

  implicit private val hc: HeaderCarrier =
    HeaderCarrier()

  private val candeService =
    mock[EuVatCandeService]

  private val upscan =
    mock[UpscanInitiateConnector]

  private val repository =
    mock[UploadRepository]

  private val appConfig =
    new AppConfig(
      Configuration(
        ConfigFactory.parseString(
          """
            |appName = euvat-refunds
            |
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

  private val now =
    Instant.parse("2026-11-12T10:00:00Z")

  private val clock =
    Clock.fixed(now, ZoneOffset.UTC)

  private val service =
    new CreateUploadTrackingService(
      candeService = candeService,
      upscan       = upscan,
      repository   = repository,
      appConfig    = appConfig,
      clock        = clock
    )

  private val applicationId =
    133L

  private val applicationDetails =
    Json
      .parse(
        """
          |{
          |  "applicationDetails": [
          |    {
          |      "applicationId": 133,
          |      "updateSeqNumber": 1
          |    }
          |  ]
          |}
          |""".stripMargin
      )
      .as[ApplicationDetailsResponse]

  private val expectedUpscanRequest =
    UpscanInitiateRequest(
      callbackUrl     = "http://localhost:18502/euvat-refunds/133/upscan-callback",
      successRedirect = "http://localhost:18501/supporting-documents/your-file",
      errorRedirect   = "http://localhost:18501/supporting-documents/upload-file",
      minimumFileSize = 1,
      maximumFileSize = 5242880
    )

  private val upscanResponse =
    UpscanInitiateResponse(
      reference = "ref-new",
      uploadRequest = UploadRequest(
        href = "https://upscan.example/upload",
        fields = Map(
          "key" -> "ref-new"
        )
      )
    )

  // The upload ID is generated as a random UUID, so tests retrieve it
  // from the UploadRecord passed to the repository.
  private def expectedRecord(
    uploadId: String
  ): UploadRecord =
    UploadRecord(
      uploadId          = uploadId,
      applicationId     = applicationId,
      reference         = "ref-new",
      fileStatus        = FileStatus.AwaitingUpload,
      uploadUrl         = "https://upscan.example/upload",
      initiateTimestamp = now,
      fields = Map(
        "key" -> "ref-new"
      ),
      downloadUrl    = None,
      uploadDetails  = None,
      failureDetails = None,
      createdAt      = now,
      updatedAt      = now
    )

  private def expectedResponse(
    uploadId: String
  ): CreateUploadTrackingResponse =
    CreateUploadTrackingResponse(
      uploadId  = uploadId,
      reference = "ref-new",
      uploadUrl = "https://upscan.example/upload",
      fields = Map(
        "key" -> "ref-new"
      )
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(
      candeService,
      upscan,
      repository
    )

  private def applicationExists(): Unit =
    when(
      candeService
        .getApplicationDetails(any[Long]())(any[HeaderCarrier])
    ).thenReturn(
      Future.successful(applicationDetails)
    )

  private def uploadStartsWith(
    inProgress: Option[UploadRecord]
  ): Unit =
    applicationExists()

    when(
      repository.findAwaitingUpload(applicationId)
    ).thenReturn(
      Future.successful(inProgress)
    )

    when(
      upscan.initiate(any[UpscanInitiateRequest]())(
        any[HeaderCarrier]
      )
    ).thenReturn(
      Future.successful(upscanResponse)
    )

    when(
      repository.replaceAwaitingUpload(
        any[UploadRecord],
        any[Option[String]]
      )
    ).thenReturn(
      Future.successful(true)
    )

  private def storedUploads(): Seq[UploadRecord] =
    val captor =
      ArgumentCaptor.forClass(classOf[UploadRecord])

    verify(repository, atLeastOnce())
      .replaceAwaitingUpload(
        captor.capture(),
        any[Option[String]]
      )

    captor.getAllValues.asScala.toSeq

  "CreateUploadTrackingService.createUploadTracking" should {

    "return ApplicationNotFound and not call Upscan when the application does not exist" in {
      when(
        candeService
          .getApplicationDetails(any[Long]())(
            any[HeaderCarrier]
          )
      ).thenReturn(
        Future.failed(
          UpstreamErrorResponse(
            message    = "Not found",
            statusCode = 404
          )
        )
      )

      val result =
        service
          .createUploadTracking(applicationId)
          .futureValue

      result shouldBe Left(
        CreateUploadTrackingError.ApplicationNotFound
      )

      verifyNoInteractions(upscan)
      verifyNoInteractions(repository)
    }

    "propagate an error and not call Upscan when the application check fails with a non-404 response" in {
      val exception =
        UpstreamErrorResponse(
          message    = "Server error",
          statusCode = 500
        )

      when(
        candeService
          .getApplicationDetails(any[Long]())(
            any[HeaderCarrier]
          )
      ).thenReturn(
        Future.failed(exception)
      )

      val thrown =
        service
          .createUploadTracking(applicationId)
          .failed
          .futureValue

      thrown shouldBe exception

      verifyNoInteractions(upscan)
      verifyNoInteractions(repository)
    }

    "initiate and store a new awaiting upload when no upload is in progress" in {
      uploadStartsWith(
        inProgress = None
      )

      val result =
        service
          .createUploadTracking(applicationId)
          .futureValue

      verify(upscan)
        .initiate(
          mockitoEq(expectedUpscanRequest)
        )(
          any[HeaderCarrier]
        )

      verify(repository)
        .replaceAwaitingUpload(
          any[UploadRecord],
          mockitoEq(None)
        )

      val stored =
        storedUploads().head

      stored.uploadId should not be empty
      stored        shouldBe expectedRecord(stored.uploadId)

      result shouldBe Right(
        expectedResponse(stored.uploadId)
      )
    }

    "replace the awaiting upload already in progress for the application" in {
      val inProgress =
        expectedRecord("upload-old").copy(
          reference = "ref-old",
          fields = Map(
            "key" -> "ref-old"
          )
        )

      uploadStartsWith(
        inProgress = Some(inProgress)
      )

      service
        .createUploadTracking(applicationId)
        .futureValue

      verify(repository)
        .replaceAwaitingUpload(
          any[UploadRecord],
          mockitoEq(Some("ref-old"))
        )

      val stored =
        storedUploads().head

      stored.uploadId     should not be "upload-old"
      stored.reference  shouldBe "ref-new"
      stored.fileStatus shouldBe FileStatus.AwaitingUpload
    }

    "create a new upload ID every time an upload is initiated" in {
      uploadStartsWith(
        inProgress = None
      )

      service
        .createUploadTracking(applicationId)
        .futureValue

      service
        .createUploadTracking(applicationId)
        .futureValue

      val uploadIds =
        storedUploads().map(_.uploadId)

      uploadIds          should have size 2
      uploadIds.distinct should have size 2
      all(uploadIds)     should not be empty
    }

    "use redirects supplied by the caller instead of the configured redirects" in {
      val successRedirect =
        "http://success"

      val errorRedirect =
        "http://error"

      uploadStartsWith(
        inProgress = None
      )

      service
        .createUploadTracking(
          applicationId   = applicationId,
          successRedirect = Some(successRedirect),
          errorRedirect   = Some(errorRedirect)
        )
        .futureValue

      verify(upscan)
        .initiate(
          mockitoEq(
            expectedUpscanRequest.copy(
              successRedirect = successRedirect,
              errorRedirect   = errorRedirect
            )
          )
        )(
          any[HeaderCarrier]
        )
    }

    "return the awaiting upload stored by another request when the insert loses the race" in {
      val storedByOther =
        expectedRecord("upload-other").copy(
          reference = "ref-other",
          fields = Map(
            "key" -> "ref-other"
          )
        )

      applicationExists()

      when(
        repository.findAwaitingUpload(applicationId)
      ).thenReturn(
        Future.successful(None),
        Future.successful(Some(storedByOther))
      )

      when(
        upscan.initiate(any[UpscanInitiateRequest]())(
          any[HeaderCarrier]
        )
      ).thenReturn(
        Future.successful(upscanResponse)
      )

      when(
        repository.replaceAwaitingUpload(
          any[UploadRecord],
          any[Option[String]]
        )
      ).thenReturn(
        Future.successful(false)
      )

      val result =
        service
          .createUploadTracking(applicationId)
          .futureValue

      result shouldBe Right(
        CreateUploadTrackingResponse(
          uploadId  = "upload-other",
          reference = "ref-other",
          uploadUrl = "https://upscan.example/upload",
          fields = Map(
            "key" -> "ref-other"
          )
        )
      )

      verify(repository, times(2))
        .findAwaitingUpload(applicationId)
    }

    "return the newly initiated upload as a fallback when the insert loses the race but no current upload can be found" in {
      applicationExists()

      when(
        repository.findAwaitingUpload(applicationId)
      ).thenReturn(
        Future.successful(None),
        Future.successful(None)
      )

      when(
        upscan.initiate(any[UpscanInitiateRequest]())(
          any[HeaderCarrier]
        )
      ).thenReturn(
        Future.successful(upscanResponse)
      )

      when(
        repository.replaceAwaitingUpload(
          any[UploadRecord],
          any[Option[String]]
        )
      ).thenReturn(
        Future.successful(false)
      )

      val result =
        service
          .createUploadTracking(applicationId)
          .futureValue

      val stored =
        storedUploads().head

      result shouldBe Right(
        expectedResponse(stored.uploadId)
      )

      verify(repository, times(2))
        .findAwaitingUpload(applicationId)
    }

    "propagate an Upscan initiation failure and not store an upload" in {
      val exception =
        UpstreamErrorResponse(
          message    = "Upscan unavailable",
          statusCode = 503
        )

      applicationExists()

      when(
        repository.findAwaitingUpload(applicationId)
      ).thenReturn(
        Future.successful(None)
      )

      when(
        upscan.initiate(any[UpscanInitiateRequest]())(
          any[HeaderCarrier]
        )
      ).thenReturn(
        Future.failed(exception)
      )

      val thrown =
        service
          .createUploadTracking(applicationId)
          .failed
          .futureValue

      thrown shouldBe exception

      verify(repository, never())
        .replaceAwaitingUpload(
          any[UploadRecord],
          any[Option[String]]
        )
    }

    "propagate a repository failure when checking for an awaiting upload" in {
      val exception =
        new RuntimeException(
          "Mongo lookup failed"
        )

      applicationExists()

      when(
        repository.findAwaitingUpload(applicationId)
      ).thenReturn(
        Future.failed(exception)
      )

      val thrown =
        service
          .createUploadTracking(applicationId)
          .failed
          .futureValue

      thrown shouldBe exception

      verifyNoInteractions(upscan)

      verify(repository, never())
        .replaceAwaitingUpload(
          any[UploadRecord],
          any[Option[String]]
        )
    }

    "propagate a repository failure when storing the awaiting upload" in {
      val exception =
        new RuntimeException(
          "Mongo insert failed"
        )

      applicationExists()

      when(
        repository.findAwaitingUpload(applicationId)
      ).thenReturn(
        Future.successful(None)
      )

      when(
        upscan.initiate(any[UpscanInitiateRequest]())(
          any[HeaderCarrier]
        )
      ).thenReturn(
        Future.successful(upscanResponse)
      )

      when(
        repository.replaceAwaitingUpload(
          any[UploadRecord],
          any[Option[String]]
        )
      ).thenReturn(
        Future.failed(exception)
      )

      val thrown =
        service
          .createUploadTracking(applicationId)
          .failed
          .futureValue

      thrown shouldBe exception
    }
  }
