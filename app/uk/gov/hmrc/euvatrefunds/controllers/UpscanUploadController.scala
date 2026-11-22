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
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.connectors.upscan.UpscanInitiateConnector
import uk.gov.hmrc.euvatrefunds.models.upscan.{Reference, UploadStatus}
import uk.gov.hmrc.euvatrefunds.services.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanUploadController @Inject() (
  authorise: AuthAction,
  upscanInitiateConnector: UpscanInitiateConnector,
  uploadProgressTracker: UploadProgressTracker,
  val cc: ControllerComponents
)(using appConfig: AppConfig, ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def createUploadTracking(
    applicationId: Long
  ): Action[AnyContent] =
    authorise.async { implicit request =>
      showResult(applicationId).flatMap { result =>
        lazy val successRedirectUrl = appConfig.upscanSuccessRedirect + result
        lazy val errorRedirectUrl = appConfig.upscanErrorRedirect + "/error"
        println(s"******** successRedirectUrl: $successRedirectUrl")
        for {
          upscanInitiateResponse <- upscanInitiateConnector.initiate(Some(successRedirectUrl), Some(errorRedirectUrl), applicationId)
          _                      <- uploadProgressTracker.requestUpload(applicationId, Reference(upscanInitiateResponse.fileReference.reference))
        } yield Ok(Json.toJson(upscanInitiateResponse))
      }
    }

  def showResult(applicationId: Long): Future[String] =
    uploadProgressTracker.getUploadResult(applicationId).map {
      case Some(UploadStatus.UploadedSuccessfully(name, mimeType, downloadUrl, size, checksum)) =>
        s"?applicationId=$applicationId&uploadStatus=UploadedSuccessfully:$name"
      case Some(UploadStatus.InProgress) => s"?applicationId=$applicationId&uploadStatus=InProgress"
      case _                             => s"?applicationId=$applicationId&uploadStatus=Failed"
    }

//    s"&status=${uploadProgressTracker.getUploadResult(applicationId)}"
//      uploadProgressTracker.getUploadResult(applicationId).map {
//        case Some(UploadStatus.UploadedSuccessfully(name, mimeType, downloadUrl, size, checksum)) =>
//          Redirect(
//            appConfig.upscanSuccessRedirect,
//            Map(
//              "applicationId" -> Seq(applicationId.toString),
//              "uploadStatus"  -> Seq("UploadedSuccessfully, " + name)
//            )
//          )
//        case Some(UploadStatus.InProgress) =>
//          Redirect(
//            appConfig.upscanSuccessRedirect,
//            Map(
//              "applicationId" -> Seq(applicationId.toString),
//              "uploadStatus"  -> Seq("InProgress")
//            )
//          )
//        case Some(UploadStatus.Failed) =>
//          Redirect(
//            appConfig.upscanSuccessRedirect,
//            Map(
//              "applicationId" -> Seq(applicationId.toString),
//              "uploadStatus"  -> Seq("Failed")
//            )
//          )
//        case None => BadRequest(s"Upload with id $applicationId not found")
//      }
//    }
