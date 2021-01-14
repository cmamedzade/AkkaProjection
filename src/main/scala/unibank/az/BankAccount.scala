package unibank.az

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}


object BankAccount {

  sealed trait Command
  sealed trait Event
  case class Add(n:Int, replyTo: ActorRef[Command],name:String) extends Command
  final case class AddBank(n:Int) extends Command
  case class Added(n:Int,name:String) extends Event
  case class State(n:Int = 0)


  def apply(): Behavior[Command] = Behaviors.setup {
    context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("Baku"),
      emptyState = State(),
      commandHandler = (state, command) => {
        command match {
          case Add(n, replyTo,name) => {
            println("from command state is:  " + state.n)
            Effect.persist(Added(n,name))
          }
          case _ => Effect.none
        }
      },
      eventHandler = (state, evt) =>  evt match {
        case Added(n,name) => state.copy(n = n)
        case _ => state
      }
    ).withTagger(_ => Set("account"))
  }
}