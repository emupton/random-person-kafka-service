package com.example.backendservice

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {
  val run: IO[Unit] = BackendserviceServer.run
}
