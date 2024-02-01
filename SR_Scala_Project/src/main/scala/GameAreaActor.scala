import akka.actor.{Actor, ActorRef}

import scala.collection.mutable
import scala.util.Random

class GameAreaActor extends Actor {

  val players: mutable.Map[String, PlayerWithActor] = mutable.LinkedHashMap[String, PlayerWithActor]()
  var foods: List[Food] = List.empty[Food]
  generateFood(10)

  override def receive: Receive = {
    case PlayerJoined(player, actor) =>
      players += (player.name -> PlayerWithActor(player, actor))
      println(s"Player joined: ${player.name}")
      notifyPlayersChanged()// Reach players' actor
    case PlayerLeft(playerName) => {
      players -= playerName
      notifyPlayersChanged()
    }
    case PlayerMoveRequest(playerName, direction) => {
      val offset = direction match {
        case "up" => Position(0, 1)
        case "down" => Position(0, -1)
        case "left" => Position(-1, 0)
        case "right" => Position(1, 0)
      }
      val oldPlayerWithActor = players(playerName)
      val oldPlayer = oldPlayerWithActor.player
      val actor = oldPlayerWithActor.actor
      var newPlayer = Player(playerName, oldPlayer.position, oldPlayer.score)
      if (!playerOnNextPosition(oldPlayer.position + offset)) {
        newPlayer = Player(playerName, oldPlayer.position + offset, oldPlayer.score + bool2int(foodOnNextPosition(oldPlayer.position + offset)))
        foods = foods.filterNot(f => newPlayer.position == f.position)
      }

      foods = foods.filterNot(f => newPlayer.position == f.position)

      players(playerName) = PlayerWithActor(newPlayer, actor)
      notifyPlayersChanged()

      if (foods.isEmpty) {
        players.values.foreach(_.actor ! GameEnded())
        players.clear()
        generateFood(10)
      }
    }
  }

  def bool2int(b: Boolean): Int = if (b) 1 else 0
  def playerOnNextPosition(position: Position): Boolean = {
    players.values.exists(_.player.position == position)
  }

  def foodOnNextPosition(position: Position): Boolean = {
    foods.exists(_.position == position)
  }
  def notifyPlayersChanged(): Unit = {
    val updatedPlayers = players.values.map(_.player).toList
    val updatedFoods = foods
    players.values.foreach(_.actor ! PlayersChanged(updatedPlayers, updatedFoods))
    println(s"Sent updated players and foods to clients: $updatedPlayers, $updatedFoods")
  }

  def generateFood(n: Int): Unit = {
    for (_ <- 1 to n) {
      var randomPosition = Position(Random.nextInt(6), Random.nextInt(6))
      while (foods.exists(_.position == randomPosition) || players.values.exists(_.player.position == randomPosition)) {
        randomPosition = Position(Random.nextInt(6), Random.nextInt(6))
      }
      foods = Food(randomPosition) :: foods
    }
  }
}

trait GameEvent
case class PlayerJoined(player: Player, actorRef: ActorRef) extends GameEvent
case class PlayerLeft(playerName: String) extends GameEvent
case class PlayerMoveRequest(playerName: String, direction: String) extends GameEvent
case class PlayersChanged(players: Iterable[Player], foods: Iterable[Food]) extends GameEvent
case class Player(name: String, position: Position, score: Int = 0)
case class PlayerWithActor(player: Player, actor: ActorRef)
case class GameEnded() extends GameEvent
case class Position(x: Int, y: Int) {
  def +(other: Position): Position = Position(x + other.x, y + other.y)
}

case class Food(position: Position)
