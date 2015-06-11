package akkastreams

import akka.actor.Actor
import akka.stream.FanInShape.{Init, Name}
import akka.stream.scaladsl._
import akka.stream.{FanInShape, Inlet, OperationAttributes}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TheActor extends Actor with ImplicitFlowMaterializer {
  override def receive: Receive = {
    case seq: Seq[Source[Int, Unit]] ⇒
      val collect = mutable.Buffer.empty[Int]
      val cachedSender = sender()

      val out: Sink[Int, Future[Unit]] = Sink.foreach(collect += _)

      val g = FlowGraph.closed(out) { implicit builder ⇒
        sink ⇒
          import FlowGraph.Implicits._

          val orderedMerge = new OrderedMerge(seq.size)
          val merge: FanInShape[Int] = builder.add(orderedMerge)

          val tuples: Seq[(Source[Int, Unit], Inlet[_])] = seq.zip(merge.inlets)

          tuples.foreach {
            case (s: Source[Int, Unit], i: Inlet[Int]) ⇒
              s ~> i
          }

          merge.out ~> sink
      }

      g.run().map(_ ⇒ cachedSender ! collect.toSeq)
  }
}

class OrderedMergeShape[A](_init: Init[A] = Name("OrderedMerge"))
  extends FanInShape[A](_init) {

  def this(count: Int) = {
    this()
    (1 to count).map(i ⇒ newInlet[A](s"input$i"))
  }

  protected override def construct(init: Init[A]) = {
    val newInstance = new OrderedMergeShape(init)
    inlets.foreach(i ⇒ newInstance.newInlet[A](i.toString + "-new"))
    newInstance
  }
}

class OrderedMerge(count: Int)
  extends FlexiMerge[Int, OrderedMergeShape[Int]](new OrderedMergeShape(count), OperationAttributes.name("OrderedMerge"))
  with LazyLogging {

  import akka.stream.scaladsl.FlexiMerge._

  override def createMergeLogic(s: OrderedMergeShape[Int]): MergeLogic[Int] = new MergeLogic[Int] {

    val valueMap: mutable.Map[Inlet[Int], Option[Int]] = mutable.Map(s.inlets.map(_.asInstanceOf[Inlet[Int]] → Option.empty[Int]): _*)

    override def initialCompletionHandling =
      CompletionHandling(
        onUpstreamFinish = (ctx, input) ⇒ {
          input match {
            case inlet: Inlet[Int] ⇒
              logger.trace("removing {}", inlet)
              valueMap.remove(inlet)
              if (valueMap.isEmpty) ctx.finish()
              SameState
          }
        },
        onUpstreamFailure = (ctx, input, cause) ⇒ {
          ctx.fail(cause)
          SameState
        })

    override def initialState: State[_] = readFromUnreadSourcesAndEventuallyEmitWhenWeHaveOneFromEach

    def readFromUnreadSourcesAndEventuallyEmitWhenWeHaveOneFromEach: State[_] = {
      def unreadInputs: immutable.Seq[Inlet[Int]] = immutable.Seq(valueMap.keys.filter(valueMap(_).isEmpty).toSeq: _*)

      State[Int](ReadAny(unreadInputs)) { (ctx, input, element) ⇒
        input match {
          case inlet: Inlet[Int] ⇒
            logger.trace("read {}", inlet)
            valueMap.put(inlet, Some(element))
            if (valueMap.values.exists(_.isEmpty)) readFromUnreadSourcesAndEventuallyEmitWhenWeHaveOneFromEach
            else {
              val (winningInlet: Inlet[Int], maybeValue: Option[Int]) = valueMap.minBy { case (_, Some(i)) ⇒ i }
              valueMap.put(winningInlet, None)
              ctx.emit(maybeValue.get)
              readFromUnreadSourcesAndEventuallyEmitWhenWeHaveOneFromEach
            }
        }
      }
    }
  }
}
