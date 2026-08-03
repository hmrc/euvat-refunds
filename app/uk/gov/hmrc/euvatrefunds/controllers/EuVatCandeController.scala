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
import uk.gov.hmrc.euvatrefunds.models.requests.{AddPurchaseRequest, ApplicationRequest, LatestApplicationRequest, SupplierTaxIdentifierCountRequest}
import uk.gov.hmrc.euvatrefunds.models.responses.SupplierTaxIdentifierCountResponse
import uk.gov.hmrc.euvatrefunds.services.EuVatCandeService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class EuVatCandeController @Inject() (
  authorise: AuthAction,
  service: EuVatCandeService,
  val cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def createApplication: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[ApplicationRequest]) match {
        case None =>
          logger.warn("Invalid JSON for ApplicationRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(appRequest) =>
          service
            .createApplication(appRequest, request.identifierValue)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while creating the refund application", ex)
              InternalServerError("Failed to create refund application")
            }
      }
    }

  def getLatestApplications: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[LatestApplicationRequest]) match {
        case Some(latestApplicationRequest) =>
          service.getLatestApplications(latestApplicationRequest).map { response =>
            Ok(Json.toJson(response))
          }
        case None =>
          Future.successful(BadRequest("Invalid request body"))
      }
    }

  def addPurchase: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[AddPurchaseRequest]) match {
        case None =>
          logger.warn("Invalid JSON for AddPurchaseRequest")
          Future.successful(BadRequest("Invalid request body"))
        case Some(purchaseRequest) =>
          service
            .addPurchase(purchaseRequest)
            .map { response =>
              Ok(Json.toJson(response))
            }
            .recover { case ex: Exception =>
              logger.error("Error while adding the purchase", ex)
              InternalServerError("Failed to add purchase")
            }
      }
    }

  def getSupplierTaxIdentifierCount: Action[AnyContent] =
    authorise.async { implicit request =>
      request.body.asJson.flatMap(_.asOpt[SupplierTaxIdentifierCountRequest]) match {
        case Some(supplierReq) =>
          service.getSupplierTaxIdentifierCount(supplierReq).map { response =>
            Ok(Json.toJson(response))
          }
        case None =>
          Future.successful(BadRequest("Invalid request body"))
      }
    }
