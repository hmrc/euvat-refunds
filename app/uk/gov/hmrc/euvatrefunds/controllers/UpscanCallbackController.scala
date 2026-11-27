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

import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.libs.json.{JsError, JsObject, Json}
import play.api.mvc.{Action, ControllerComponents, MaxSizeExceeded, Result}
import uk.gov.hmrc.euvatrefunds.models.upload.UpscanCallbackRequest
import uk.gov.hmrc.euvatrefunds.services.{UpscanCallbackResult, UpscanCallbackService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class UpscanCallbackController @Inject() (
  cc: ControllerComponents,
  service: UpscanCallbackService
)(using
  ExecutionContext,
  Materializer
) extends BackendController(cc)
    with Logging:

  final def callback(
    applicationId: Long
  ): Action[Either[MaxSizeExceeded, String]] =
    Action(
      parse.maxLength(
        UpscanCallbackController.maxBodySize,
        parse.tolerantText
      )
    ).async { request =>
      request.body match
        case Left(MaxSizeExceeded(length)) =>
          logger.warn(
            s"Upscan callback body for application $applicationId exceeded $length bytes"
          )

          Future.successful(
            BadRequest(
              Json.obj(
                "errorMessage" -> s"Body exceeded the maximum of $length bytes",
                "errorCode"    -> "PAYLOAD_TOO_LARGE"
              )
            )
          )

        case Right(body) =>
          parseCallback(applicationId, body)
    }

  private def parseCallback(
    applicationId: Long,
    body: String
  ): Future[Result] =
    Try(Json.parse(body)).toOption match
      case Some(json: JsObject) =>
        json
          .validate[UpscanCallbackRequest]
          .fold(
            errors => {
              logger.warn(
                s"Invalid Upscan callback payload for application $applicationId: ${JsError.toJson(errors)}"
              )

              Future.successful(
                BadRequest(
                  Json.obj(
                    "errorMessage" -> "Invalid Upscan callback payload",
                    "errorCode"    -> "INVALID_JSON_FORMAT"
                  )
                )
              )
            },
            callback => handleCallback(applicationId, callback)
          )

      case _ =>
        logger.warn(
          s"Malformed JSON received in Upscan callback for application $applicationId"
        )

        Future.successful(
          BadRequest(
            Json.obj(
              "errorMessage" -> "Malformed JSON",
              "errorCode"    -> "MALFORMED_JSON"
            )
          )
        )

  private def handleCallback(
    applicationId: Long,
    callback: UpscanCallbackRequest
  ): Future[Result] =
    service
      .handleCallback(applicationId, callback)
      .map {
        case UpscanCallbackResult.Accepted =>
          Ok

        case UpscanCallbackResult.AlreadyHandled =>
          logger.info(
            s"Upscan redelivered an already handled callback for application $applicationId, reference ${callback.reference}"
          )
          Ok

        case UpscanCallbackResult.StaleReference =>
          logger.warn(
            s"Upscan callback received for application $applicationId with unknown or stale reference ${callback.reference}"
          )
          Ok

        case UpscanCallbackResult.MissingDownloadUrl =>
          logger.warn(
            s"Upscan READY callback for application $applicationId had no downloadUrl, reference ${callback.reference}"
          )
          Ok
      }
      .recover { case ex: Exception =>
        logger.error(
          s"Failed to handle Upscan callback for application $applicationId",
          ex
        )

        InternalServerError(
          Json.obj(
            "errorMessage" -> "Could not record the callback",
            "errorCode"    -> "UPSCAN_CALLBACK_ERROR"
          )
        )
      }

object UpscanCallbackController:

  val maxBodySize: Long = 10240
