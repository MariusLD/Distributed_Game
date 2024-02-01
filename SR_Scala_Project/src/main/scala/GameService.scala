import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}

class GameService(implicit val actorSystem : ActorSystem, implicit val actorMaterialize: ActorMaterializer) extends Directives {

  val websocketRoute: Route = (get & parameter("playerName")) { playerName =>
    handleWebSocketMessages(flow(playerName))
  }

  private val gameAreaActor: ActorRef = actorSystem.actorOf(Props(new GameAreaActor()))
  private val playerActorSource: Source[GameEvent, ActorRef] = Source.actorRef[GameEvent](10, OverflowStrategy.fail)

  def flow(playerName: String): Flow[Message, Message, Any] = Flow.fromGraph(GraphDSL.create(playerActorSource) { implicit builder =>
    playerActor =>
      import GraphDSL.Implicits._
      // initial block for players
      val materialization = builder.materializedValue.map(playerActorRef => PlayerJoined(Player(playerName, Position(0, 0)), playerActorRef))
      val merge = builder.add(Merge[GameEvent](2))

      // flow that converts messages to game events
      val messagesToGameEventsFlow = builder.add(Flow[Message].map {
        case TextMessage.Strict(direction) => PlayerMoveRequest(playerName, direction)
      })

      // flow that converts game events to messages
      val gameEventsToMessagesFlow = builder.add(Flow[GameEvent].map {
        case PlayersChanged(players, foods) =>
          import spray.json._
          import DefaultJsonProtocol._
          implicit val positionFormat: RootJsonFormat[Position] = jsonFormat2(Position)
          implicit val playerFormat: RootJsonFormat[Player] = jsonFormat3(Player)
          implicit val foodFormat: RootJsonFormat[Food] = jsonFormat1(Food)

          val playersJson = players.toJson.toString
          val foodsJson = foods.toJson.toString

          val combinedJson = s"""{"players": $playersJson, "foods": $foodsJson}"""
          TextMessage(combinedJson)
        case GameEnded() => TextMessage("Game ended")
      })

      val gameAreaActorSink = Sink.actorRef[GameEvent](gameAreaActor, PlayerLeft(playerName))

      materialization ~> merge ~> gameAreaActorSink
      messagesToGameEventsFlow ~> merge
      playerActor ~> gameEventsToMessagesFlow

      FlowShape(messagesToGameEventsFlow.in, gameEventsToMessagesFlow.out)
  })
}