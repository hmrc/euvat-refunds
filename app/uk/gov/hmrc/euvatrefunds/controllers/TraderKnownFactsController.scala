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

import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.euvatrefunds.connectors.DatacacheProxyConnector
import uk.gov.hmrc.euvatrefunds.errors.SystemException
import uk.gov.hmrc.euvatrefunds.models.GetKnownFactsRequest
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TraderKnownFactsController @Inject() (
  cc: ControllerComponents,
  connector: DatacacheProxyConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc):

  def getKnownFacts(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val maybeVrn = request.body.validate[GetKnownFactsRequest].asOpt.map(_.vrn.trim).filter(_.nonEmpty)

    maybeVrn match {
      case None =>
        Future.failed(new SystemException("VAT registration number is missing"))
      case Some(vrn) =>
        connector.getTraderKnownFacts(vrn).map {
          case Some(facts) if facts.tradeClass.exists(_.trim.nonEmpty) => Ok(Json.toJson(facts))
          case _                                                       => throw new SystemException("Business activity code1 is missing")
        }
    }
  }
