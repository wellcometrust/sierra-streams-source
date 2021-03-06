package uk.ac.wellcome.sierra

import java.net.SocketTimeoutException
import java.time.temporal.ChronoUnit
import java.time.Instant

import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.Scenario
import io.circe.Json
import io.circe.optics.JsonPath.root
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}

class SierraStreamSourceTest
    extends AnyFunSpec
    with SierraWireMock
    with Matchers
    with ScalaFutures
    with ExtendedPatience {
  implicit val system = ActorSystem()
  implicit val materializer = Materializer(system)

  it("should read from sierra") {
    val eventualJson = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
      "items", Map.empty).take(1).runWith(Sink.head[Json])
    whenReady(eventualJson) { json =>
      root.id.string.getOption(json) shouldBe Some("1000001")
    }
  }

  it("should paginate through results") {
    val sierraSource = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
      "items",
      Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

    val eventualJsonList = sierraSource.runWith(Sink.seq[Json])

    whenReady(eventualJsonList) { jsonList =>
      jsonList should have size 157
    }
  }

  it("should refresh the access token if receives a unauthorized response") {
    stubFor(get(urlMatching("/bibs")).inScenario("refresh token")
      .whenScenarioStateIs(Scenario.STARTED).willReturn(aResponse().withStatus(401))
      .atPriority(1).willSetStateTo("token expired"))

    stubFor(get(urlMatching("/token")).inScenario("refresh token")
      .whenScenarioStateIs("token expired").willSetStateTo("token refreshed"))

    stubFor(get(urlMatching("/bibs")).inScenario("refresh token")
      .whenScenarioStateIs("token refreshed"))

    val eventualJson = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)(
      "bibs", Map.empty).take(1).runWith(Sink.head[Json])

    whenReady(eventualJson) { json =>
      root.id.string.getOption(json) shouldBe Some("1000001")
    }
  }

  it("should return a sensible error message if it fails to authorize with the sierra api") {
    stubFor(get(urlMatching("/bibs")).willReturn(aResponse().withStatus(401)))

    val eventualJson = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret)("bibs", Map.empty).take(1).runWith(Sink.head[Json])

    whenReady(eventualJson.failed) { ex =>
      ex shouldBe a [RuntimeException]
      ex.getMessage should include ("Unauthorized")
    }
  }

  it("should obey the throttle rate for sierra api requests") {

    val sierraSource = SierraSource(sierraWireMockUrl, oauthKey, oauthSecret, ThrottleRate(4, 1.second))(
      "items", Map("updatedDate" -> "[2013-12-10T17:16:35Z,2013-12-13T21:34:35Z]"))

    val eventualJsonList = sierraSource.runWith(Sink.seq[Json])
    val startTime = Instant.now()
    val expectedDurationInMillis = 1000L

    whenReady(eventualJsonList) { jsonList =>
      val gap: Long = ChronoUnit.MILLIS.between(startTime, Instant.now())

      gap shouldBe >(expectedDurationInMillis)
    }
  }

  it("respects the specified timeout parameter") {
    // The default timeout is 10000 ms, so with default settings we'd
    // expect to get a 200 OK for this response.
    stubFor(
      get(urlMatching("/bibs")).willReturn(
        aResponse()
          .withStatus(200)
          .withFixedDelay(5000)
      )
    )

    val source = SierraSource(
      apiUrl = sierraWireMockUrl,
      oauthKey = oauthKey,
      oauthSecret = oauthSecret,
      timeoutMs = 200
    )("bibs", Map.empty)

    val future = source.take(1).runWith(Sink.head[Json])

    whenReady(future.failed) { ex =>
      ex shouldBe a [SocketTimeoutException]
    }
  }
}

trait ExtendedPatience extends PatienceConfiguration {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(40, Seconds)),
    interval = scaled(Span(150, Millis))
  )
}
