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
import uk.gov.hmrc.euvatrefunds.services.{CreateUploadTrackingError, CreateUploadTrackingService, EuVatFileUploadService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.ExecutionContext

// all supporting-document upload endpoints; each action uses its own service
class FileUploadController @Inject() (
  authorise: AuthAction,
  createUploadTrackingService: CreateUploadTrackingService,
  uploadSummaryService: EuVatFileUploadService,
  val cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def createUploadTracking(
    applicationId: Long,
    successRedirect: Option[String],
    errorRedirect: Option[String]
  ): Action[AnyContent] =
    authorise.async { implicit request =>
      createUploadTrackingService
        .createUploadTracking(applicationId, successRedirect, errorRedirect)
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

  def getUploadSummary(applicationId: Long): Action[AnyContent] =
    authorise.async { _ =>
      uploadSummaryService
        .getUploadSummary(applicationId)
        .map(summary => Ok(Json.toJson(summary)))
        .recover { case ex =>
          logger.error(s"Error retrieving upload summary for applicationId: $applicationId: ${ex.getMessage}", ex)
          InternalServerError("Currently experiencing issues with our service")
        }
    }
<<<<<<<< HEAD:app/uk/gov/hmrc/euvatrefunds/controllers/FileUploadController.scala
========

  def deleteUploadTracking(applicationId: Long): Action[AnyContent] =
    authorise.async { _ =>
      uploadSummaryService
        .deleteUploadTracking(applicationId)
        .map(result => Ok(Json.toJson(result)))
        .recover { case ex =>
          logger.error(s"Error deleting upload tracking for applicationId: $applicationId: ${ex.getMessage}", ex)
          InternalServerError("Currently experiencing issues with our service")
        }
    }

  def deleteUploadTrackingByReference(applicationId: Long, reference: String): Action[AnyContent] =
    authorise.async { _ =>
      uploadSummaryService
        .deleteUploadTrackingByReference(applicationId, reference)
        .map(result => Ok(Json.toJson(result)))
        .recover { case ex =>
          logger.error(s"Error deleting upload tracking for applicationId: $applicationId and reference: $reference: ${ex.getMessage}", ex)
          InternalServerError("Currently experiencing issues with our service")
        }
    }

}
>>>>>>>> 879ed45 (DTR-9617: F6.2 - Delete Upload Tracking):app/uk/gov/hmrc/euvatrefunds/controllers/EuVatFileUploadController.scala
