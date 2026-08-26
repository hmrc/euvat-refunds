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
import uk.gov.hmrc.euvatrefunds.models.requests.{AddPurchaseRequest, ApplicationRequest, GetPurchaseDetailsRequest, LatestApplicationRequest}
import uk.gov.hmrc.euvatrefunds.models.responses.{AddPurchaseResponse, ApplicationResponse, GetPurchaseDetailsResponse, LatestApplicationResponse, TraderKnownFactsResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EuVatStubsConnector @Inject() (
  appConfig: AppConfig,
  http: HttpClientV2
)(implicit ec: ExecutionContext):

  private val baseUrl: String = appConfig.baseUrl("euvat-stubs") + "/euvat-stubs"

  def getTraderKnownFacts(vrn: String)(implicit hc: HeaderCarrier): Future[TraderKnownFactsResponse] =
    http
      .get(url"$baseUrl/traders/get-known-facts/$vrn")
      .execute[TraderKnownFactsResponse]

  def getLatestApplications(request: LatestApplicationRequest)(implicit hc: HeaderCarrier): Future[LatestApplicationResponse] =
    http
      .post(url"$baseUrl/get-latest-application")
      .withBody(Json.toJson(request))
      .execute[LatestApplicationResponse]

  def getSupplierTaxIdentifierCount(
    request: uk.gov.hmrc.euvatrefunds.models.requests.SupplierTaxIdentifierCountRequest
  )(implicit hc: HeaderCarrier): Future[uk.gov.hmrc.euvatrefunds.models.responses.SupplierTaxIdentifierCountResponse] =
    http
      .post(url"$baseUrl/get-supplier-taxIdentifier-count")
      .withBody(Json.toJson(request))
      .execute[uk.gov.hmrc.euvatrefunds.models.responses.SupplierTaxIdentifierCountResponse]

  def createApplication(request: ApplicationRequest, vrn: String)(implicit hc: HeaderCarrier): Future[ApplicationResponse] =
    http
      .post(url"$baseUrl/create-application/$vrn")
      .withBody(Json.toJson(request))
      .execute[ApplicationResponse]

  def addPurchase(request: AddPurchaseRequest)(implicit hc: HeaderCarrier): Future[AddPurchaseResponse] =
    http
      .post(url"$baseUrl/add-purchase")
      .withBody(Json.toJson(request))
      .execute[AddPurchaseResponse]

  def getPurchaseDetails(request: GetPurchaseDetailsRequest)(implicit hc: HeaderCarrier): Future[GetPurchaseDetailsResponse] =
    http
      .post(url"$baseUrl/get-purchase-details")
      .withBody(Json.toJson(request))
      .execute[GetPurchaseDetailsResponse]
