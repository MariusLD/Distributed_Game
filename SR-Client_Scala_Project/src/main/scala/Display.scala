import akka.Done
import akka.actor.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import ressources.{Food, Player}
import scalafx.application.Platform
import scalafx.scene.control.TextInputDialog
import scalafx.scene.layout.{AnchorPane, StackPane}
import scalafx.scene.paint.Color
import scalafx.scene.shape.{Circle, Rectangle}
import scalafx.scene.text.Text

import scala.concurrent.{Future, Promise}

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
