import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import ressources.{Food, Player}

import scala.concurrent.{Future, Promise}
import scala.io.StdIn

object Main {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    val name = StdIn.readLine("What's your name?")
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

    val gui = new GUI(keyBoardHandler, display)
    val sink = display.sink(input, display, args, client, keyBoardHandler, output, gui)

    print("Starting client")
    val ((inputMat, result), outputMat) = client.run(input, sink)
    promise.success(inputMat)

    gui.main(args)
  }
}
