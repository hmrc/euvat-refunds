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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.euvatrefunds.connectors.DatacacheProxyConnector
import uk.gov.hmrc.euvatrefunds.models.TraderKnownFacts
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TraderKnownFactsControllerSpec extends AnyWordSpec with Matchers {

  private val connector  = mock(classOf[DatacacheProxyConnector])
  private val controller = new TraderKnownFactsController(stubControllerComponents(), connector)

  private val facts = TraderKnownFacts(vrn = "123456789", traderName = Some("ABC GmbH"), tradeClass = Some("8765"))

  "TraderKnownFactsController.getByVrn" should {

    "return 200 with the trader known facts as JSON when found" in {
      when(connector.getTraderKnownFacts(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(facts)))

      val result = controller.getByVrn("123456789")(FakeRequest())

      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts)
    }

    "return 404 when the trader is not found" in {
      when(connector.getTraderKnownFacts(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      val result = controller.getByVrn("999999999")(FakeRequest())

      status(result) shouldBe NOT_FOUND
    }
  }
}
