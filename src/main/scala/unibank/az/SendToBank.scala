package unibank.az

import BankAccount._
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.cassandra.scaladsl.CassandraProjection
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.AtLeastOnceProjection
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn.readLine

object SendToBank {

  var n = readLine().toInt
  var name = readLine()

  def apply(): Behavior[Command] = Behaviors.setup{
    context =>
      createTables(system)
      val pr = context.spawn(ProjectionBehavior(createProjectionFor(system,settings,index = 1)),"projection")

      val bankAccount = context.spawn(BankAccount(),"bankAccount")
      bankAccount ! Add(n,context.self,name)
      Behaviors.same
  }

  def createProjectionFor(
                           system: ActorSystem[_],
                           settings: EventProcessorSettings,
                           index: Int): AtLeastOnceProjection[Offset, EventEnvelope[BankAccount.Event]] = {
    val tag = "account"
    val sourceProvider = EventSourcedProvider.eventsByTag[BankAccount.Event](
      system = system,
      readJournalPluginId = CassandraReadJournal.Identifier,
      tag = tag)
    CassandraProjection.atLeastOnce(
      projectionId = ProjectionId("bank-account", tag),
      sourceProvider,
      handler = () => new BankProjectionHandler(tag, system))
  }
    .withSaveOffset(1,10.millis)

  // config.withFallback(ConfigFactory.defaultReference())
  val config = ConfigFactory.load("application.conf")
  val system = ActorSystem(SendToBank(),"my-system",config)
  val settings = EventProcessorSettings(system)

  def main(args: Array[String]): Unit = {
    system
  }

  def createTables(system: ActorSystem[_]): Unit = {
    val session =
      CassandraSessionRegistry(system).sessionFor("alpakka.cassandra")

    val offsetTableStmt =
      """
      CREATE TABLE IF NOT EXISTS akka_projection.offset_store (
        projection_name text,
        partition int,
        projection_key text,
        offset text,
        manifest text,
        last_updated timestamp,
        PRIMARY KEY ((projection_name, partition), projection_key)
      )
      """
    Await.ready(session.executeDDL(offsetTableStmt), 30.seconds)
    system.log.info("Created akka_projection.offset_store table")

  }
}
