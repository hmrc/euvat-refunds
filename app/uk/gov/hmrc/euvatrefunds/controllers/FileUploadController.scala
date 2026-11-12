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

import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.services.{CreateUploadTrackingError, CreateUploadTrackingService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

class FileUploadController @Inject() (
  authorise: AuthAction,
  createUploadTrackingService: CreateUploadTrackingService,
  val cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def createUploadTracking(applicationId: Long): Action[AnyContent] =
    authorise.async { implicit request =>
      createUploadTrackingService
        .createUploadTracking(applicationId)
        .map {
          case Right(response) =>
            Created(Json.toJson(response))
          case Left(CreateUploadTrackingError.ApplicationNotFound) =>
            logger.warn(s"No application found with id $applicationId")
            NotFound(s"No application found with id $applicationId")
        }
        .recover { case ex: Exception =>
          logger.error(s"Error while creating upload tracking for application $applicationId", ex)
          InternalServerError("Failed to create upload tracking")
        }
    }
