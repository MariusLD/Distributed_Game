import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source}
import ressources.{Food, Player}
import scalafx.application.Platform
import scalafx.scene.control.TextInputDialog
import scalafx.scene.layout.{AnchorPane, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.{Circle, Rectangle}
import scalafx.scene.text.Text

import scala.concurrent.{Future, Promise}
import scala.io.StdIn

class Display() {
  private val PlayerRadius = 100
  private val Dimensions = 6
  private val ScreenSize = PlayerRadius * Dimensions
  val panel: AnchorPane = new AnchorPane {
    minWidth = ScreenSize
    minHeight = ScreenSize
  }

  def sink(input: Source[String, ActorRef], display: Display, args: Array[String], client: Client, keyBoardHandler: KeyBoardHandler, output: Sink[Either[String, (List[Player], List[Food])], Future[Done]], gui: GUI): Sink[Either[String, (List[Player], List[Food])], Future[Done]] = Sink.foreach[Either[String, (List[Player], List[Food])]] {
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
            implicit val system = ActorSystem()
            implicit val materializer = ActorMaterializer()
            val client = new Client(name)
            val display = new Display()
            val input = Source.actorRef[String](5, OverflowStrategy.dropNew)

            val promise = Promise[ActorRef]()
            val futureInputMat = promise.future

            val keyBoardHandler = new KeyBoardHandler(futureInputMat)

            // Define output before calling display.sink
            val output: Sink[Either[String, (List[Player], List[Food])], Future[Done]] = Sink.foreach {
              case Left(gameEndedMessage) => println(gameEndedMessage)
              case Right((players, foods)) => println(s"Players: $players, Foods: $foods")
            }

            val sink = display.sink(input, display, args, client, keyBoardHandler, output, gui)

            print("Starting client")
            val ((inputMat, result), outputMat) = client.run(input, sink)
            promise.success(inputMat)

            gui.updateGameState(keyBoardHandler, display)
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
