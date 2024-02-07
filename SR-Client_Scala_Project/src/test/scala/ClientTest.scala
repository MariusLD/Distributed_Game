import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.concurrent.Future

class ClientTest extends AnyFunSuite with Matchers {
  test("should be able to login player"){
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val testSink: Sink[Message, TestSubscriber.Probe[Message]] = TestSink.probe[Message]
    // send this as a message over the WebSocket
    val outgoing = Source.empty[Message]

    // flow to use (note: not re-usable!)
    val webSocketFlow: Flow[Message, Message, Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest("ws://localhost:8080/?playerName=Bob"))

    // the materialized value is a tuple with
    // upgradeResponse is a Future[WebSocketUpgradeResponse] that
    // completes or fails when the connection succeeds or fails
    // and closed is a Future[Done] with the stream completion from the incoming sink
    val (upgradeResponse, testProbe) =
      outgoing
        .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(testSink)(Keep.both) // also keep the Future[Done]
        .run()

    testProbe.request(1)
    testProbe.expectNext(TextMessage.Strict("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}}]"))
  }
}