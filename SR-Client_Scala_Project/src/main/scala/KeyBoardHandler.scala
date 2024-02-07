import akka.actor.ActorRef
import scalafx.scene.input.{KeyCode, KeyEvent}
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global

class KeyBoardHandler(var keyboardEventsReceiver: Future[ActorRef]) {
  def handle(keyEvent: KeyEvent) = keyEvent.code match {
    case KeyCode.Up => keyboardEventsReceiver.map(_ ! "down") //scalafx coordinates are reversed
    case KeyCode.Down => keyboardEventsReceiver.map(_ ! "up")
    case KeyCode.Left => keyboardEventsReceiver.map(_ ! "left")
    case KeyCode.Right => keyboardEventsReceiver.map(_ ! "right")
  }
}
