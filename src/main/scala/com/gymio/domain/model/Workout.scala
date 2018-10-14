package com.gymio.domain.model

import java.util.UUID

import cats.effect.IO
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

case class Workout(workoutId: Int, userId: UUID, day: Int, week: Int, events: Seq[Event])

object Workout {
  implicit val decoder: EntityDecoder[IO, Workout] = jsonOf[IO, Workout]
}