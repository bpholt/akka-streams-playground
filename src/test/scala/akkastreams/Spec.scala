package akkastreams

import akka.actor.{ActorSystem, Props}
import akka.stream.scaladsl._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.{AfterEach, Scope}

import scala.collection.immutable
import scala.concurrent.duration._

class Spec extends Specification with Mockito {
  "Akka-Streams" should {
    "merge (last element is in shorter input)" in new Setup {
      private val actorRef = TestActorRef(Props[TheActor])

      val s1 = Source(immutable.Seq(1, 2, 4))
      val s2 = Source(immutable.Seq(3, 5))

      actorRef ! Seq(s1, s2)

      expectMsgPF(timeout) {
        case msg ⇒ msg must_== (1 to 5)
      }
    }

    "merge (last element is in longer input)" in new Setup {
      private val actorRef = TestActorRef(Props[TheActor])

      val s1 = Source(immutable.Seq(1, 2, 4, 6))
      val s2 = Source(immutable.Seq(3, 5))

      actorRef ! Seq(s1, s2)

      expectMsgPF(timeout) {
        case msg ⇒ msg must_== (1 to 6)
      }
    }

    "pull everything from one input" in new Setup {
      private val actorRef = TestActorRef(Props[TheActor])

      val s1 = Source(1 to 1000)
      val s2 = Source.empty

      actorRef ! Seq(s1, s2)

      expectMsgPF(timeout) {
        case msg ⇒ msg must_== (1 to 1000)
      }
    }
  }
}

class Setup(_system: ActorSystem)
  extends TestKit(_system)
  with Scope
  with AfterEach
  with ImplicitSender {

  import scala.language.postfixOps

  val timeout = 2 seconds

  override def after = system.shutdown()

  def this() = this(ActorSystem("test-system"))
}
