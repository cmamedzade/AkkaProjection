package unibank.az
import akka.Done
import akka.actor.Status.Success
import akka.actor.typed.ActorSystem
import akka.actor.typed.eventstream.EventStream
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import akka.actor.typed.ActorSystem
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import unibank.az.BankAccount.Added

class BankProjectionHandler(tag: String, system: ActorSystem[_])
extends  Handler[EventEnvelope[BankAccount.Event]] {

  val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext = system.executionContext
  val session = CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

  override def process(envelope: EventEnvelope[BankAccount.Event]): Future[Done] = {
    envelope.event match {
      case Added(n,name) =>
      //  val stmt = s"select * from bankaccount.account WHERE id = $n"
        val insert = s"select * from  bankaccount.account ;"
         session.selectOne(insert).onComplete(value => println("value: "+value.get.head.getFormattedContents))


        println("envelop event is: "+n)
        log.info(
          s"number added: $n",
          tag,
          envelope.event,
          envelope.persistenceId,
          envelope.sequenceNr
        )
    }
    system.eventStream ! EventStream.Publish(envelope.event)
    Future.successful(Done)
  }
}
