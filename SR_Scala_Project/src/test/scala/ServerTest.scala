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
      val receivedMessage = wsClient.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0},\"score\":0}], \"foods\": ["))
    }
  }

  test("should register player") {
    assertWebsocket("Bob"){ wsClient =>
        val receivedMessage = wsClient.expectMessage()
        assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0},\"score\":0}], \"foods\": ["))
    }
  }

  test("should register multiple players") {
    val gameService = new GameService()
    val wsClientBob = WSProbe()
    val wsClientAlice = WSProbe()

    WS(s"/?playerName=Bob", wsClientBob.flow) ~> gameService.websocketRoute ~> check {
      val receivedMessage = wsClientBob.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0},\"score\":0}], \"foods\": ["))
    }

    WS(s"/?playerName=Alice", wsClientAlice.flow) ~> gameService.websocketRoute ~> check {
      val receivedMessage = wsClientAlice.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0},\"score\":0},{\"name\":\"Alice\",\"position\":{\"x\":0,\"y\":0},\"score\":0}], \"foods\": ["))
    }
  }

  test("should register player and move it up") {
    assertWebsocket("Bob"){ wsClient =>
      var receivedMessage = wsClient.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":0}"))
      wsClient.sendMessage("up")
      receivedMessage = wsClient.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":1}"))
    }
  }

  test("should have collision"){
    val gameService = new GameService()
    val wsClientBob = WSProbe()
    val wsClientAlice = WSProbe()

    WS(s"/?playerName=Bob", wsClientBob.flow) ~> gameService.websocketRoute ~> check {
      var receivedMessage = wsClientBob.expectMessage()
      wsClientBob.sendMessage("up")
      receivedMessage = wsClientBob.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":1}"))
    }

    WS(s"/?playerName=Alice", wsClientAlice.flow) ~> gameService.websocketRoute ~> check {
      var receivedMessage = wsClientAlice.expectMessage()
      wsClientAlice.sendMessage("up")
      receivedMessage = wsClientAlice.expectMessage()
      assert(receivedMessage.toString().contains("{\"players\": [{\"name\":\"Bob\",\"position\":{\"x\":0,\"y\":1},\"score\":0},{\"name\":\"Alice\",\"position\":{\"x\":0,\"y\":0}"))
    }
  }

  test("should have food"){
    assertWebsocket("Bob"){ wsClient =>
      val receivedMessage = wsClient.expectMessage()
      assert(!receivedMessage.toString().contains("\"score\":10}], \"foods\": []"))
    }
  }

  test("should eat all food"){
    val gameService = new GameService()
    val wsClientBob = WSProbe()
    val wsClientAlice = WSProbe()

    WS(s"/?playerName=Bob", wsClientBob.flow) ~> gameService.websocketRoute ~> check {
      wsClientBob.sendMessage("up")
      wsClientBob.sendMessage("down")
      var receivedMessage = wsClientBob.expectMessage()
      for (_ <- 1 to 3) {
        for (_ <- 1 to 6) {
          wsClientBob.sendMessage("up")
          wsClientBob.expectMessage()
        }
        wsClientBob.sendMessage("right")
        for (_ <- 1 to 6) {
          wsClientBob.sendMessage("down")
          wsClientBob.expectMessage()
        }
        wsClientBob.sendMessage("right")
      }
      for (_ <- 1 to 6) {
        wsClientBob.sendMessage("down")
        wsClientBob.expectMessage()
      }

      receivedMessage = wsClientBob.expectMessage()
      assert(receivedMessage.toString().contains("\"score\":10}], \"foods\": []}"))
    }
  }
   //test collision avec bordure
  def assertWebsocket(playerName: String)( assertions:(WSProbe) => Unit) : Unit = {
    val gameService = new GameService()
    val wsClient = WSProbe()
    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.websocketRoute ~> check(assertions(wsClient))
  }

  //
}