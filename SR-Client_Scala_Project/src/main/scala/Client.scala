import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import ressources.{Food, Player, Position}
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future

class Client(var playerName: String)(implicit val actorSystem: ActorSystem, implicit val actorMaterializer: ActorMaterializer) extends DefaultJsonProtocol {

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
