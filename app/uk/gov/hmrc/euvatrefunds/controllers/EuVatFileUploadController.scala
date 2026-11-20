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

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.services.EuVatFileUploadService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EuVatFileUploadController @Inject() (
  authorise: AuthAction,
  uploadSummaryService: EuVatFileUploadService,
  val cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

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
