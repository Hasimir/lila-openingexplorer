package lila.openingexplorer

import ornicar.scalalib.Validation
import scala.util.matching.Regex
import scala.util.Random

import chess.Color

case class GameRef(
    gameId: String,
    winner: Option[Color],
    speed: SpeedGroup,
    averageRating: Int) extends PackHelper {

  def pack: Array[Byte] = {
    val packedGameId = gameId.zip(gameId.indices.reverse).map {
      case (c, i) =>
        GameRef.base.indexOf(c) * math.pow(GameRef.base.size, i).toLong
    } sum

    val packedSpeed = speed.id << 14

    val packedWinner = winner match {
      case Some(Color.White) => 2 << 12
      case Some(Color.Black) => 1 << 12
      case None              => 0
    }

    // must be between 0 and 4095
    val packedRating = math.min((1 << 12) - 1, math.max(0, averageRating))

    packUint16(packedWinner | packedSpeed | packedRating) ++ packUint48(packedGameId)
  }

}

object GameRef extends PackHelper with Validation {

  val packSize = 8

  private val base = ('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z')

  private def unpackGameId(v: Long): String = {
    def decodeGameId(v: Long, res: List[Char] = Nil): List[Char] = {
      val quotient = v / base.size
      if (quotient > 0)
        decodeGameId(quotient, base((v % base.size).toInt) :: res)
      else
        base(v.toInt) :: res
    }

    decodeGameId(v).mkString.reverse.padTo(8, base(0)).reverse
  }

  def unpack(packed: Array[Byte]): GameRef = {
    val metaXorRating = unpackUint16(packed)

    val winner = (metaXorRating >> 12) & 0x3 match {
      case 2 => Some(Color.White)
      case 1 => Some(Color.Black)
      case _ => None
    }

    val speed = SpeedGroup.byId.getOrElse((metaXorRating >> 14) & 0x3, SpeedGroup.Classical)

    val averageRating = metaXorRating & 0xfff

    GameRef(
      unpackGameId(unpackUint48(packed.drop(2))),
      winner,
      speed,
      averageRating
    )
  }

  def fromPgn(game: chess.format.pgn.ParsedPgn): Valid[GameRef] =
    if (game.sans.size < 10) Left("Too few moves")
    else {
      val gameId = game.tag("LichessID") orElse {
        game.tag("FICSGamesDBGameNo") flatMap parseLongOption map unpackGameId
      } getOrElse Random.alphanumeric.take(8).mkString

      val winner = game.tag("Result") match {
        case Some("1-0") => Some(Color.White)
        case Some("0-1") => Some(Color.Black)
        case _           => None
      }

      val speed = SpeedGroup.fromTimeControl(game.tag("TimeControl").getOrElse("-"))

      val averageRating: Option[Int] = {
        val ratings = chess.Color.all.flatMap { c =>
          game.tag(s"${c}Elo").flatMap(parseIntOption)
        }
        if (ratings.nonEmpty) Some(ratings.sum / ratings.size) else None
      }

      averageRating.fold[Either[String, GameRef]](Left("No rating")) { rating =>
        Right(new GameRef(gameId, winner, speed, rating))
      }
    }
}
