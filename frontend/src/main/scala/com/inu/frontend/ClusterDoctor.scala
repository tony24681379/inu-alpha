package com.inu.frontend

import akka.actor.{Actor, ActorLogging, DeadLetter}

/**
  * Created by henry on 1/11/17.
  */
class ClusterDoctor extends  Actor with ActorLogging {

  override def receive: Receive = {
    case DeadLetter(msg, from, to) =>
      context.system.terminate()
  }
}
