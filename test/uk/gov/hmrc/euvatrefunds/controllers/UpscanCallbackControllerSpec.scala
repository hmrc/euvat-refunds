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

package uk.gov.hmrc.euvatrefunds.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq as mockitoEq}
import org.mockito.Mockito.{never, reset, verify, when}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.euvatrefunds.models.upload.*
import uk.gov.hmrc.euvatrefunds.services.{UpscanCallbackResult, UpscanCallbackService}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class UpscanCallbackControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach with BeforeAndAfterAll:

  implicit val ec: ExecutionContext =
    ExecutionContext.global

  implicit val actorSystem: ActorSystem =
    ActorSystem("UpscanCallbackControllerSpec")

  implicit val materializer: Materializer =
    Materializer(actorSystem)

  private val service =
    mock[UpscanCallbackService]

  private val applicationId = 12345L
  private val reference = "reference-123"
  private val downloadUrl = "https://example.com/download"

  private val uploadDetails =
    UploadDetails(
      fileName        = "test.pdf",
      fileMimeType    = "application/pdf",
      uploadTimestamp = Instant.parse("2026-11-23T10:00:00Z"),
      checksum        = "checksum-123",
      size            = 987L
    )

  private val readyCallback =
    UpscanCallbackRequest.Ready(
      reference     = reference,
      downloadUrl   = downloadUrl,
      uploadDetails = uploadDetails
    )

  private val failedCallback =
    UpscanCallbackRequest.Failed(
      reference = reference,
      failureDetails = FailureDetails(
        failureReason = FailureReason.Rejected,
        message       = "MIME type is not allowed"
      )
    )

  private val readyBody =
    s"""
       |{
       |  "reference": "$reference",
       |  "downloadUrl": "$downloadUrl",
       |  "fileStatus": "READY",
       |  "uploadDetails": {
       |    "fileName": "test.pdf",
       |    "fileMimeType": "application/pdf",
       |    "uploadTimestamp": "2026-11-23T10:00:00Z",
       |    "checksum": "checksum-123",
       |    "size": 987
       |  }
       |}
       |""".stripMargin

  private val failedBody =
    s"""
       |{
       |  "reference": "$reference",
       |  "fileStatus": "FAILED",
       |  "failureDetails": {
       |    "failureReason": "REJECTED",
       |    "message": "MIME type is not allowed"
       |  }
       |}
       |""".stripMargin

  private def postRequest(body: String) =
    FakeRequest(
      POST,
      s"/$applicationId/upscan-callback"
    ).withBody(body)

  private def controller =
    new UpscanCallbackController(
      Helpers.stubControllerComponents(),
      service
    )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    reset(service)

  override protected def afterAll(): Unit =
    try
      actorSystem.terminate()
    finally
      super.afterAll()

  "POST /:applicationId/upscan-callback" should {

    "return 200 when a READY callback is accepted" in {
      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(readyCallback)
        )
      ).thenReturn(
        Future.successful(UpscanCallbackResult.Accepted)
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(readyBody)
        )

      status(result) shouldBe OK

      verify(service).handleCallback(
        applicationId,
        readyCallback
      )
    }

    "return 200 when a FAILED callback is accepted" in {
      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(failedCallback)
        )
      ).thenReturn(
        Future.successful(UpscanCallbackResult.Accepted)
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(failedBody)
        )

      status(result) shouldBe OK

      verify(service).handleCallback(
        applicationId,
        failedCallback
      )
    }

    "return 200 when the callback has already been handled" in {
      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(readyCallback)
        )
      ).thenReturn(
        Future.successful(UpscanCallbackResult.AlreadyHandled)
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(readyBody)
        )

      status(result) shouldBe OK

      verify(service).handleCallback(
        applicationId,
        readyCallback
      )
    }

    "return 200 when the callback reference is stale" in {
      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(readyCallback)
        )
      ).thenReturn(
        Future.successful(UpscanCallbackResult.StaleReference)
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(readyBody)
        )

      status(result) shouldBe OK

      verify(service).handleCallback(
        applicationId,
        readyCallback
      )
    }

    "return 200 when a READY callback has an empty downloadUrl" in {
      val callback =
        readyCallback.copy(
          downloadUrl = ""
        )

      val body =
        (
          Json.parse(readyBody).as[JsObject] +
            ("downloadUrl" -> JsString(""))
        ).toString

      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(callback)
        )
      ).thenReturn(
        Future.successful(UpscanCallbackResult.MissingDownloadUrl)
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(body)
        )

      status(result) shouldBe OK

      verify(service).handleCallback(
        applicationId,
        callback
      )
    }

    "return 400 when a required READY field is missing" in {
      val body =
        (
          Json.parse(readyBody).as[JsObject] -
            "downloadUrl"
        ).toString

      val result =
        call(
          controller.callback(applicationId),
          postRequest(body)
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "INVALID_JSON_FORMAT"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 400 when a required FAILED field is missing" in {
      val body =
        (
          Json.parse(failedBody).as[JsObject] -
            "failureDetails"
        ).toString

      val result =
        call(
          controller.callback(applicationId),
          postRequest(body)
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "INVALID_JSON_FORMAT"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 400 when fileStatus is unsupported" in {
      val body =
        (
          Json.parse(readyBody).as[JsObject] +
            ("fileStatus" -> JsString("PROCESSING"))
        ).toString

      val result =
        call(
          controller.callback(applicationId),
          postRequest(body)
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "INVALID_JSON_FORMAT"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 400 when the body is not valid JSON" in {
      val result =
        call(
          controller.callback(applicationId),
          postRequest("not json")
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "MALFORMED_JSON"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 400 when the body is valid JSON but not a JSON object" in {
      val result =
        call(
          controller.callback(applicationId),
          postRequest("[]")
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "MALFORMED_JSON"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 400 when the body exceeds the maximum allowed size" in {
      val oversized =
        Json
          .parse(readyBody)
          .as[JsObject] +
          (
            "padding" ->
              JsString(
                "a" * UpscanCallbackController.maxBodySize.toInt
              )
          )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(oversized.toString)
        )

      status(result) shouldBe BAD_REQUEST

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "PAYLOAD_TOO_LARGE"

      verify(service, never())
        .handleCallback(
          any[Long],
          any[UpscanCallbackRequest]
        )
    }

    "return 500 when the callback service fails" in {
      when(
        service.handleCallback(
          mockitoEq(applicationId),
          mockitoEq(readyCallback)
        )
      ).thenReturn(
        Future.failed(
          new RuntimeException("Mongo unavailable")
        )
      )

      val result =
        call(
          controller.callback(applicationId),
          postRequest(readyBody)
        )

      status(result) shouldBe INTERNAL_SERVER_ERROR

      (contentAsJson(result) \ "errorCode")
        .as[String] shouldBe "UPSCAN_CALLBACK_ERROR"

      (contentAsJson(result) \ "errorMessage")
        .as[String] shouldBe "Could not record the callback"

      verify(service).handleCallback(
        applicationId,
        readyCallback
      )
    }
  }
