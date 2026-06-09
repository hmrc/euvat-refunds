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

package uk.gov.hmrc.euvatrefunds.services

import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.euvatrefunds.connectors.RdsCandeProxyConnector
import uk.gov.hmrc.euvatrefunds.models.responses.TraderKnownFactsResponse
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class EuVatRefundServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: RdsCandeProxyConnector = mock[RdsCandeProxyConnector]

  val service = new EuVatRefundService(mockConnector)

  "EuVatRefundService.retrieveDirectDebits" should {

    "return the response from the connector" in {
      val expectedResponse = TraderKnownFactsResponse(
        traderName   = Some("Test Trader"),
        vatRegNumber = 123456
      )

      when(mockConnector.getTraderKnownFacts()(any()))
        .thenReturn(Future.successful(expectedResponse))

      val result = service.retrieveDirectDebits().futureValue

      result shouldBe expectedResponse
      verify(mockConnector, times(1)).getTraderKnownFacts()(any())
    }

    "propagate an exception from the connector" in {
      val failure = new RuntimeException("Connector failed")

      when(mockConnector.getTraderKnownFacts()(any()))
        .thenReturn(Future.failed(failure))

      val result = service.retrieveDirectDebits()

      whenReady(result.failed) { ex =>
        ex shouldBe failure
      }
    }
  }
}
