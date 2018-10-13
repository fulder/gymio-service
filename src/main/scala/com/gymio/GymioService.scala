package com.gymio

import java.util.UUID

import cats.data.Kleisli
import cats.effect._
import com.gymio.domain.model._
import com.gymio.domain.service.ExerciseLogService
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Request, Response}

class GymioService {
  var log: Map[UUID, ExerciseLog] = Map()
  var eventStore: Seq[Event]      = List()

  val gymioService: Kleisli[IO, Request[IO], Response[IO]] = HttpRoutes
    .of[IO] {
      case GET -> Root / "log" =>
        Ok(log.asJson)
      case req @ POST -> Root / "log" / UUIDVar(userId) / "add" =>
        logExerciseForUser(req, userId)
    }
    .orNotFound

  def logExerciseForUser(req: Request[IO], userId: UUID): IO[Response[IO]] = {
    val userLog = log.get(userId).getOrElse(ExerciseLog(List()))

    for {
      c   <- req.as[Command]
      l   <- IO(userLog)
      e   <- IO.fromEither(ExerciseLogService.decide(c)(l))
      _   <- updateStore(e)
      _   <- updateLog(userId, e, l)
      res <- Ok(log.asJson)
    } yield res
  }

  def updateLog(userId: UUID, event: Event, exerciseLog: ExerciseLog): IO[Map[UUID, ExerciseLog]] = {

    log += userId -> ExerciseLogService.applyEvent(event)(exerciseLog)
    IO(log)
  }

  def updateStore(event: Event): IO[Seq[Event]] = {
    eventStore = eventStore :+ event
    IO(eventStore)
  }

}
