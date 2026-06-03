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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.euvatrefunds.actions.AuthAction
import uk.gov.hmrc.euvatrefunds.errors.SystemException
import uk.gov.hmrc.euvatrefunds.models.TraderKnownFactsResponse
import uk.gov.hmrc.euvatrefunds.services.EuVatService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TraderKnownFactsControllerSpec extends AnyWordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("TraderKnownFactsControllerSpec")
  private implicit val materializer: Materializer = Materializer(system)

  override def afterAll(): Unit = {
    val _ = system.terminate()
    super.afterAll()
  }

  private val service = mock(classOf[EuVatService])
  private val auth = mock(classOf[AuthAction])
  private val controller = new TraderKnownFactsController(auth, service, stubControllerComponents())

  private val facts = TraderKnownFactsResponse(vatRegNumber = 123456789, traderName = Some("ABC GmbH"), tradeClass = Some("8765"))

  private def postKnownFacts(body: JsValue): Future[Result] =
    call(controller.getKnownFacts(), FakeRequest(POST, "/traders/getKnownFacts"), body)

  "TraderKnownFactsController.getKnownFacts" should {

    "return 200 with the trader known facts as JSON when found with a business activity code" in {
      when(service.retrieveDirectDebits()(any[HeaderCarrier]))
        .thenReturn(Future.successful(facts))

      val result = postKnownFacts(Json.obj("vrn" -> "123456789"))

      status(result)        shouldBe OK
      contentAsJson(result) shouldBe Json.toJson(facts)
    }

    "fail with a SystemException when the trader is found but the business activity code is missing" in {
      when(service.retrieveDirectDebits()(any[HeaderCarrier]))
        .thenReturn(Future.successful(facts.copy(tradeClass = None)))

      postKnownFacts(Json.obj("vrn" -> "123456789")).failed.futureValue shouldBe a[SystemException]
    }

    "fail with a SystemException when the VRN is missing from the request" in {
      postKnownFacts(Json.obj()).failed.futureValue shouldBe a[SystemException]
    }
  }
}
