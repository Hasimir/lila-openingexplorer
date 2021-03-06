package lila.openingexplorer

case class Entry(sub: Map[(RatingGroup, SpeedGroup), SubEntry]) {

  def subEntry(ratingGroup: RatingGroup, speedGroup: SpeedGroup): SubEntry =
    sub.getOrElse((ratingGroup, speedGroup), SubEntry.empty)

  def subEntries(groups: List[(RatingGroup, SpeedGroup)]): List[SubEntry] =
    groups.map((g) => subEntry(g._1, g._2))

  def totalGames = sub.values.map(_.totalGames).sum

  def maxPerWinnerAndGroup = sub.values.map(_.maxPerWinner).max

  def withGameRef(game: GameRef): Entry = {
    val ratingGroup = RatingGroup.find(game.averageRating)
    copy(sub = sub + ((ratingGroup, game.speed) -> subEntry(ratingGroup, game.speed).withGameRef(game)))
  }

  def withExistingGameRef(game: GameRef): Entry = {
    val ratingGroup = RatingGroup.find(game.averageRating)
    copy(sub = sub + ((ratingGroup, game.speed) -> subEntry(ratingGroup, game.speed).withExistingGameRef(game)))
  }

  def gameRefs(groups: List[(RatingGroup, SpeedGroup)]): List[GameRef] =
    subEntries(groups)
      .map(_.gameRefs)
      .flatMap(_.zipWithIndex).sortBy(_._2).map(_._1) // interleave

  def whiteWins(groups: List[(RatingGroup, SpeedGroup)]): Long =
    subEntries(groups).map(_.whiteWins).sum

  def draws(groups: List[(RatingGroup, SpeedGroup)]): Long =
    subEntries(groups).map(_.draws).sum

  def blackWins(groups: List[(RatingGroup, SpeedGroup)]): Long =
    subEntries(groups).map(_.blackWins).sum

  def averageRatingSum(groups: List[(RatingGroup, SpeedGroup)]): Long =
    subEntries(groups).map(_.averageRatingSum).sum

  def numGames(groups: List[(RatingGroup, SpeedGroup)]): Long =
    subEntries(groups).map(_.totalGames).sum

  def averageRating(groups: List[(RatingGroup, SpeedGroup)]): Int = {
    val games = numGames(groups)
    if (games == 0) 0 else (averageRatingSum(groups) / games).toInt
  }

  lazy val allGameRefs = gameRefs(Entry.allGroups)
  def totalWhiteWins = whiteWins(Entry.allGroups)
  def totalDraws = draws(Entry.allGroups)
  def totalBlackWins = blackWins(Entry.allGroups)
  def totalAverageRatingSum = averageRatingSum(Entry.allGroups)
}

object Entry {

  def empty = Entry(Map.empty)

  def fromGameRef(game: GameRef) = Entry.empty.withGameRef(game)

  def groups(
    ratings: List[RatingGroup],
    speeds: List[SpeedGroup]): List[(RatingGroup, SpeedGroup)] = {
    // cross product
    for {
      ratingGroup <- ratings
      speedGroup <- speeds
    } yield (ratingGroup, speedGroup)
  }

  def groups(speed: SpeedGroup): List[(RatingGroup, SpeedGroup)] =
    groups(RatingGroup.all, List(speed))

  val allGroups = groups(RatingGroup.all, SpeedGroup.all)
}
