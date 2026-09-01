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

package uk.gov.hmrc.euvatrefunds.connectors

import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.euvatrefunds.config.AppConfig
import uk.gov.hmrc.euvatrefunds.models.requests.*
import uk.gov.hmrc.euvatrefunds.models.responses.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RdsCandeProxyConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(implicit ec: ExecutionContext):

  private val baseUrl: String = appConfig.baseUrl("rds-cande-proxy") + "/rds-cande-proxy"

  def createApplication(request: ApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationResponse] =
    http
      .post(url"$baseUrl/euvat/create-application")
      .withBody(Json.toJson(request))
      .execute[ApplicationResponse]

  def getLatestApplications(request: LatestApplicationRequest)(implicit hc: HeaderCarrier): Future[LatestApplicationResponse] =
    http
      .post(url"$baseUrl/euvat/get-latest-application")
      .withBody(Json.toJson(request))
      .execute[LatestApplicationResponse]

  def addPurchase(request: AddPurchaseRequest)(implicit hc: HeaderCarrier): Future[AddPurchaseResponse] =
    http
      .post(url"$baseUrl/euvat/add-purchase")
      .withBody(Json.toJson(request))
      .execute[AddPurchaseResponse]

  def updatePurchaseDetails(request: UpdatePurchaseRequest)(implicit hc: HeaderCarrier): Future[UpdatePurchaseResponse] =
    http
      .put(url"$baseUrl/euvat/update-purchase-details")
      .withBody(Json.toJson(request))
      .execute[UpdatePurchaseResponse]

  def getSupplierTaxIdentifierCount(
    request: SupplierTaxIdentifierCountRequest
  )(implicit hc: HeaderCarrier): Future[SupplierTaxIdentifierCountResponse] =
    http
      .post(url"$baseUrl/euvat/get-supplier-taxIdentifier-count")
      .withBody(Json.toJson(request))
      .execute[SupplierTaxIdentifierCountResponse]

  def getSupplierVrnCount(request: SupplierVrnCountRequest)(implicit hc: HeaderCarrier): Future[SupplierVrnCountResponse] =
    http
      .post(url"$baseUrl/euvat/get-supplier-vrn-count")
      .withBody(Json.toJson(request))
      .execute[SupplierVrnCountResponse]
