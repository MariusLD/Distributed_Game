import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.scaladsl.TestSink
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scalafx.application.{JFXApp, Platform}
import scalafx.scene.Scene
import scalafx.scene.input.{KeyCode, KeyEvent}
import scalafx.scene.layout.{AnchorPane, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.Circle
import scalafx.scene.text.Text
import scalafx.Includes._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.io.StdIn

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

  test("should be able to move player") {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val client = new Client("Bob")
    val input = Source.actorRef[String](5, OverflowStrategy.dropNew)
    val output = TestSink.probe[List[Player]]
    //    sinkResult         testSinkProbe
    val ((inputMat, result), outputMat) = client.run(input, output) // output -> list of players

    inputMat ! "up"

    outputMat.request(2)
    outputMat.expectNext(List(Player("Bob", Position(0, 0))))
    outputMat.expectNext(List(Player("Bob", Position(0, 1))))
  }
}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val name = StdIn.readLine("What's your name?")
    val client = new Client(name)
    val display = new Display()
    val input = Source.actorRef[String](5,OverflowStrategy.dropNew)
    val output = display.sink
    val ((inputMat,result),outputMat) = client.run(input,output)
    val keyBoardHandler = new KeyBoardHandler(inputMat)
    new GUI(keyBoardHandler,display).main(args)
  }
}

class Client(playerName : String)(implicit val actorSystem: ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends DefaultJsonProtocol {
  import spray.json._
  implicit val positionFormat: RootJsonFormat[Position] = jsonFormat2(Position)
  implicit val playerFormat: RootJsonFormat[Player] = jsonFormat2(Player)

  val webSocketFlow: Flow[Message, List[Player], Future[WebSocketUpgradeResponse]] = Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8080/?playerName=$playerName")).collect {
    case TextMessage.Strict(strMsg) => strMsg.parseJson.convertTo[List[Player]]
  }

  // 10:59
  def run[M1,M2](input: Source[String, M1], output: Sink[List[Player], M2]): ((M1, Future[WebSocketUpgradeResponse]), M2) = {
    input.map(direction => TextMessage(direction))
      .viaMat(webSocketFlow)(Keep.both)
      .toMat(output)(Keep.both)
      .run()
  }
}

case class Player(name: String, position: Position)

case class Position(x: Int, y: Int)

class KeyBoardHandler(keyboardEventsReceiver: ActorRef) {
  def handle(keyEvent: KeyEvent) = keyEvent.code match {
    case KeyCode.Up => keyboardEventsReceiver ! "down" //scalafx coordinates are reversed
    case KeyCode.Down => keyboardEventsReceiver ! "up"
    case KeyCode.Left => keyboardEventsReceiver ! "left"
    case KeyCode.Right => keyboardEventsReceiver ! "right"
  }
}

class Display() {
  private val PlayerRadius = 100
  private val Dimensions = 6
  private val ScreenSize = PlayerRadius * Dimensions
  val panel: AnchorPane = new AnchorPane {
    minWidth = ScreenSize
    minHeight = ScreenSize
  }

  def sink: Sink[List[Player], Future[Done]] = Sink.foreach[List[Player]] { playerPositions=>
    val playersShapes = playerPositions.map(player => {
      new StackPane {
        minWidth = ScreenSize
        minHeight = ScreenSize
        layoutX = player.position.x * PlayerRadius
        layoutY = player.position.y * PlayerRadius
        prefHeight = PlayerRadius
        prefWidth = PlayerRadius
        val circlePlayer: Circle = new Circle {
          radius = PlayerRadius * 0.5
          fill = getColorForPlayer(player.name)
        }
        val textOnCircle: Text = new Text {
          text = player.name
        }
        children = Seq(circlePlayer, textOnCircle)

        def getColorForPlayer(name: String): Color = {
          val r = 55 + math.abs(("r" + name).hashCode) % 200
          val g = 55  + math.abs(("g"+ name).hashCode) % 200
          val b = 55  + math.abs(("b" + name).hashCode) % 200
          Color.rgb(r, g, b)
        }
      }
    })
    Platform.runLater({
      panel.children = playersShapes
      panel.requestFocus()
    })
  }
}

class GUI(keyBoardHandler: KeyBoardHandler, display: Display) extends JFXApp {
  stage = new JFXApp.PrimaryStage {
    title.value = "client"
    scene = new Scene {
      content = display.panel
      onKeyPressed = (event: KeyEvent) => keyBoardHandler.handle(event)
    }
  }
}