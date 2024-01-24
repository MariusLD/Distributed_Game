import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class ServerTest extends AnyFunSuite with Matchers with ScalatestRouteTest {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  test("should create empty GameService") {
    new GameService()
  }

  test("should be able to connect to the GameService websocket") {
    assertWebsocket("Bob"){ wsClient =>
      isWebSocketUpgrade shouldEqual true
    }
  }

  test("should respond with correct message") {
    assertWebsocket("Bob"){ wsClient =>
      wsClient.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}}]")
    }
  }

  test("should register player") {
    assertWebsocket("Bob"){ wsClient =>
      wsClient.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}}]")
    }
  }

  test("should register multiple players") {
    val gameService = new GameService()
    val wsClientBob = WSProbe()
    val wsClientAlice = WSProbe()

    WS(s"/?playerName=Bob", wsClientBob.flow) ~> gameService.websocketRoute ~> check {
      wsClientBob.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}}]")
    }

    WS(s"/?playerName=Alice", wsClientAlice.flow) ~> gameService.websocketRoute ~> check {
      wsClientAlice.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}},{\"name\":\"Alice\",\"position\":{\"x\":0,\"y\":0}}]")
    }
  }

  test("should register player and move it up") {
    assertWebsocket("Bob"){ wsClient =>
      wsClient.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}}]")
      wsClient.sendMessage("up")
      wsClient.expectMessage("[{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":1}}]")
    }
  }

  def assertWebsocket(playerName: String)( assertions:(WSProbe) => Unit) : Unit = {
    val gameService = new GameService()
    val wsClient = WSProbe()
    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.websocketRoute ~> check(assertions(wsClient))
  }
}