import MovieGetter.Movie
import MovieManager.MovieGet
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer {
  def main(args: Array[String]) {
    implicit val movieFormat = jsonFormat4(Movie)
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher
    val movieManager = system.actorOf(Props[MovieManager], "movieManager")

    val route: Route =
      get {
        pathPrefix("movie" /  PathMatchers.Segment ) { id:String =>
          println(id)
          implicit val timeout = Timeout(5 seconds)
          val movie: Future[Movie] = (movieManager ? MovieGet(id.toString)).mapTo[Movie]
          complete(movie)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}