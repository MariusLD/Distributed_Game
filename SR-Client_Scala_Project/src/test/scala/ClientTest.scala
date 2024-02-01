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
import scalafx.scene.shape.{Circle, Rectangle}
import scalafx.scene.text.Text
import scalafx.Includes._
import scalafx.scene.control.{Alert, ButtonType, TextInputDialog}
import scalafx.scene.control.Alert.AlertType
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
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
}

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val name = StdIn.readLine("What's your name?")
    val client = new Client(name)
    val display = new Display()
    val input = Source.actorRef[String](5,OverflowStrategy.dropNew)

    val promise = Promise[ActorRef]()
    val futureInputMat = promise.future

    val keyBoardHandler = new KeyBoardHandler(futureInputMat)

    // Define output before calling display.sink
    val output: Sink[Either[String, (List[Player], List[Food])], Future[Done]] = Sink.foreach {
      case Left(gameEndedMessage) => println(gameEndedMessage)
      case Right((players, foods)) => println(s"Players: $players, Foods: $foods")
    }

    val sink = display.sink(input, display, args, client, keyBoardHandler, output)

    print("Starting client")
    val ((inputMat,result),outputMat) = client.run(input,sink)
    promise.success(inputMat)

    new GUI(keyBoardHandler,display).main(args)
  }
}

class Client(var playerName : String)(implicit val actorSystem: ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends DefaultJsonProtocol {
  import spray.json._
  implicit val positionFormat: RootJsonFormat[Position] = jsonFormat2(Position)
  implicit val foodFormat: RootJsonFormat[Food] = jsonFormat1(Food)
  implicit val playerFormat: RootJsonFormat[Player] = jsonFormat3(Player)

  val webSocketFlow: Flow[Message, Either[String, (List[Player], List[Food])], Future[WebSocketUpgradeResponse]] =
    Http().webSocketClientFlow(WebSocketRequest(s"ws://localhost:8080/?playerName=$playerName")).collect {
      case TextMessage.Strict(strMsg) =>
        if (strMsg == "Game ended") {
          Left(strMsg)
        } else {
          val parsed = strMsg.parseJson.asJsObject
          val players = parsed.fields("players").convertTo[List[Player]]
          val foods = parsed.fields("foods").convertTo[List[Food]]
          Right((players, foods))
        }
    }

  // 10:59
  def run[M1, M2](input: Source[String, M1], output: Sink[Either[String, (List[Player], List[Food])], M2]): ((M1, Future[WebSocketUpgradeResponse]), M2) = {
    input.map(direction => TextMessage(direction))
      .viaMat(webSocketFlow)(Keep.both)
      .toMat(output)(Keep.both)
      .run()
  }
}

case class Player(name: String, position: Position, score: Int = 0)
case class Food(position: Position)
case class Position(x: Int, y: Int)

class KeyBoardHandler(var keyboardEventsReceiver: Future[ActorRef]) {
  def handle(keyEvent: KeyEvent) = keyEvent.code match {
    case KeyCode.Up => keyboardEventsReceiver.map(_ ! "down") //scalafx coordinates are reversed
    case KeyCode.Down => keyboardEventsReceiver.map(_ ! "up")
    case KeyCode.Left => keyboardEventsReceiver.map(_ ! "left")
    case KeyCode.Right => keyboardEventsReceiver.map(_ ! "right")
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

  def sink(input: Source[String, ActorRef], display: Display, args: Array[String], client: Client, keyBoardHandler: KeyBoardHandler, output: Sink[Either[String, (List[Player], List[Food])], Future[Done]]): Sink[Either[String, (List[Player], List[Food])], Future[Done]] = Sink.foreach[Either[String, (List[Player], List[Food])]] {
    case Left(gameEndedMessage) =>
      Platform.runLater {
        val dialog = new TextInputDialog() {
          title = "Game Ended"
          headerText = gameEndedMessage
          contentText = "The game has ended. Do you want to rejoin the game? Please enter your name:"
        }
        val result = dialog.showAndWait()
        result match {
          case Some(name) =>
            client.playerName = name
            val promise = Promise[ActorRef]()
            val ((inputMat: ActorRef, result), outputMat) = client.run(input, output)
            promise.success(inputMat)
            keyBoardHandler.keyboardEventsReceiver = promise.future
          case None =>
            System.exit(0)
        }
      }
    case Right((players, foods)) =>
      val playerShapes = players.map(player => createPlayerShape(player, players))
      val foodShapes = foods.map(createFoodShape)
      val shapes = playerShapes ++ foodShapes

      Platform.runLater {
        panel.children = shapes
        panel.requestFocus()
      }
  }

  def createPlayerShape(player: Player, allPlayers: List[Player]): StackPane = {
    val isBestPlayer = getBestPlayers(allPlayers).contains(player)
    val circleColor = if (isBestPlayer) Color.Gold else Color.Blue
    val playerName = player.name + " " + player.score + (if (isBestPlayer) " 👑" else "")
    new StackPane {
      layoutX = player.position.x * PlayerRadius
      layoutY = player.position.y * PlayerRadius
      children = Seq(new Circle {
        radius = PlayerRadius * 0.5
        fill = circleColor
      }, new Text {
        text = playerName
      })
    }
  }

  def getBestPlayers(players: List[Player]): List[Player] = {
    val maxScore = players.map(_.score).max
    players.filter(_.score == maxScore)
  }

  def createFoodShape(food: Food): Rectangle = {
    new Rectangle {
      x = food.position.x * PlayerRadius
      y = food.position.y * PlayerRadius
      width = PlayerRadius
      height = PlayerRadius
      fill = Color.Green
    }
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