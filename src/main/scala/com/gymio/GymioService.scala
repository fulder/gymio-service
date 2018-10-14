package com.gymio

import java.util.UUID

import cats.data.Kleisli
import cats.effect._
import com.gymio.domain.model._
import com.gymio.domain.service.{ExerciseLogService, WorkoutService}
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Request, Response}

class GymioService {
  var log: Map[UUID, ExerciseLog]           = Map()
  var eventStore: Seq[Event]                = List()
  var workoutStore: Map[UUID, Seq[Workout]] = Map()
  var activeWorkout: Map[UUID, Workout]     = Map()

  val gymioService: Kleisli[IO, Request[IO], Response[IO]] = HttpRoutes
    .of[IO] {
      case GET -> Root / "log" =>
        Ok(log.asJson)
      case req @ POST -> Root / "log" / UUIDVar(userId) / "add" =>
        logExerciseForUser(req, userId)
      case req @ POST -> Root / "workout" / UUIDVar(userId) / "complete" =>
        completeWorkout(req, userId)
      case GET -> Root / "workout" / UUIDVar(userId) =>
        getActiveWorkout(userId)
      case req @ POST -> Root / "workout" / UUIDVar(userId) / "start" =>
        setActiveWorkout(userId)
  }
    .orNotFound

  def getActiveWorkout(userId: UUID): IO[Response[IO]] = {
    val workout = activeWorkout.get(userId)
    if (workout.isDefined) {
      Ok(workout.asJson)
    } else {
      NotFound()
    }
  }

  def logExerciseForUser(req: Request[IO], userId: UUID): IO[Response[IO]] = {
    val userLog = log.getOrElse(userId, ExerciseLog(List()))

    for {
      c   <- req.as[Command]
      e   <- IO.fromEither(ExerciseLogService.decide(c)(userLog))
      _   <- updateStore(e)
      _   <- updateLog(userId, e, userLog)
      res <- Accepted(log.asJson)
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

  def completeWorkout(req: Request[IO], userId: UUID): IO[Response[IO]] = {
    updateWorkoutStore(userId)
    activeWorkout -= userId
    Accepted(workoutStore.asJson)
  }

  def updateWorkoutStore(userId: UUID) = {
    activeWorkout.get(userId).foreach(s => {
      val sessionList = workoutStore.getOrElse(userId, List()) :+ s
      workoutStore += userId -> sessionList
    })
  }

  def setActiveWorkout(userId: UUID) = {
    val lastWorkout = workoutStore.getOrElse(userId, List(Workout(1, userId, 3, 0, List()))).last
    activeWorkout = Map(userId -> WorkoutService.getNextWorkout(lastWorkout))
    Accepted(activeWorkout.asJson)
  }
}
