/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.euvatrefunds.services

import com.google.inject.Inject
import play.api.Configuration
import uk.gov.hmrc.euvatrefunds.connectors.{EuVatStubsConnector, RdsCacheProxyConnector}
import uk.gov.hmrc.euvatrefunds.models.responses.TraderKnownFactsResponse
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EuVatCacheService @Inject() (
  rdsDatacacheProxyConnector: RdsCacheProxyConnector,
  euVatStubsConnector: EuVatStubsConnector,
  configuration: Configuration
) {

  private val cacheStubbed: Boolean = configuration.get[Boolean]("feature-switch.rds-cache-stubbed")

  def retrieveTraderKnownFacts(vrn: String)(implicit hc: HeaderCarrier): Future[TraderKnownFactsResponse] = {
    if (cacheStubbed) {
      euVatStubsConnector.getTraderKnownFacts(vrn)
    } else {
      rdsDatacacheProxyConnector.getTraderKnownFacts()
    }
  }

}
