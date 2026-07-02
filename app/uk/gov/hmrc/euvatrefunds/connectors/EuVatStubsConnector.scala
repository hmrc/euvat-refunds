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
import uk.gov.hmrc.euvatrefunds.models.requests.ApplicationRequest
import uk.gov.hmrc.euvatrefunds.models.responses.{ApplicationResponse, TraderKnownFactsResponse}
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
      .get(url"$baseUrl/traders/getKnownFacts/$vrn")
      .execute[TraderKnownFactsResponse]

  def createApplication(request: ApplicationRequest)(implicit hc: HeaderCarrier): Future[ApplicationResponse] =
    http
      .post(url"$baseUrl/create-application")
      .withBody(Json.toJson(request))
      .execute[ApplicationResponse]
