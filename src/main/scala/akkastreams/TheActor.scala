package akkastreams

import akka.actor.Actor
import akka.stream.scaladsl._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class TheActor extends Actor with ImplicitFlowMaterializer {
  override def receive: Receive = {
    case seq: Seq[Source[Int, Unit]] ⇒
      val collect = mutable.Buffer.empty[Int]
      val cachedSender = sender()

      val out: Sink[Int, Future[Unit]] = Sink.foreach(collect += _)

      val g = FlowGraph.closed(out) { implicit builder ⇒
        sink ⇒
          import FlowGraph.Implicits._

          val merge = builder.add(Merge[Int](seq.size))
          seq.foreach(_ ~> merge)
          merge ~> sink
      }

      g.run().map(_ ⇒ cachedSender ! collect.sorted.toSeq)
  }
}
