package okay.wordle

import okay.business.scala3.*
import okay.wordle.Rules.Colour.*
import okay.wordle.Wordle.App

/** WORDLE BY SCRIPT: the whole application, its word fixed, its results in memory, played as a person would */
class WordleSuite extends munit.FunSuite:
  def play(words: Seq[String], acts: Act*): (Played[Unit], List[Result]) =
    val results = Results.inMemory()
    (Play(steps(Model.toy(new scala.util.Random(7))(results(Secret.fixed(words.head, words.tail*)(
      Wordle.main[App])))), acts*), results.kept)
  def guess(w: String): Act = Act.press("Guess", "Guess" -> w)
  def texts(p: Played[?]): List[String] = p.at.toList.flatMap(_.parts).collect {
    case Part.Text(s) => s
    case Part.Notice(_, s) => s
  }
  def board(p: Played[?]): String = p.at.toList.flatMap(_.parts).collect { case Part.Picture(_, _, _, t) => t }
    .mkString

  test("the colours, repeated letters as Wordle counts them") {
    assertEquals(Rules.colours("babes", "abbey"), Vector(Yellow, Yellow, Green, Green, Grey))
    assertEquals(Rules.colours("geese", "eerie"), Vector(Grey, Green, Yellow, Grey, Green))
    assertEquals(Rules.colours("crane", "crane"), Vector.fill(5)(Green))
  }

  test("a round solved: the board, the end, the result kept, the menu again") {
    val (p, kept) = play(Seq("crane"), Act.press("New game"), guess("slate"), guess("CRANE"),
      Act.press("Back to the menu"))
    assertEquals(p.seen, List("Wordle", "Wordle", "Wordle", "Solved", "Wordle"))
    assertEquals(kept, List(Result(2)))
    assertEquals(p.at.map(_.title), Some("Wordle"))
  }

  test("the end of a solved round shows the board and the colours") {
    val (p, _) = play(Seq("crane"), Act.press("New game"), guess("slate"), guess("crane"))
    assertEquals(p.at.map(_.title), Some("Solved"))
    assertEquals(board(p), "slate ⬜⬜🟩⬜🟩\ncrane 🟩🟩🟩🟩🟩")
    assert(texts(p).contains("Solved in 2 of 6!"), texts(p))
  }

  test("a guess refused at its field: not five letters, not a word; the board unchanged") {
    val (short, _) = play(Seq("crane"), Act.press("New game"), guess("cran"))
    assertEquals(short.problems.map(_.message), List("five letters, a to z"))
    val (unknown, _) = play(Seq("crane"), Act.press("New game"), guess("zzzzz"))
    assertEquals(unknown.problems.map(_.message), List("not in the dictionary"))
    assertEquals(board(unknown), "an empty board")
  }

  test("hard mode: a guess against the colours so far is refused") {
    val (p, _) = play(Seq("crane"), Act.press("Hard mode"), guess("slate"), guess("tulip"))
    assertEquals(p.problems.map(_.message), List("hard mode: it must fit the colours so far"))
    val (ok, _) = play(Seq("crane"), Act.press("Hard mode"), guess("slate"), guess("crane"))
    assertEquals(ok.at.map(_.title), Some("Solved"))
  }

  test("the hint: how many words are still possible, the solver's pick among them") {
    val (p, _) = play(Seq("crane"), Act.press("New game"), guess("slate"), Act.press("Hint"))
    val n = Rules.possible(Vector(Rules.Move("slate", Rules.colours("slate", "crane")))).size
    assert(texts(p).exists(_.startsWith(s"$n words still possible; the solver would try ")), texts(p))
  }

  test("six misses lose the round: the word told, the loss kept") {
    val misses = Seq("slate", "pious", "dumpy", "booth", "fight", "wrung").map(guess)
    val (p, kept) = play(Seq("crane"), (Act.press("New game") +: misses)*)
    assertEquals(p.at.map(_.title), Some("Not this time"))
    assert(texts(p).contains("The word was CRANE."), texts(p))
    assertEquals(kept, List(Result(0)))
  }

  test("give up: a loss; the statistics count every round") {
    val (p, kept) = play(Seq("crane", "slate"), Act.press("New game"), guess("crane"), Act.press("Back to the menu"),
      Act.press("New game"), Act.press("Give up"), Act.press("Back to the menu"), Act.press("Statistics"))
    assertEquals(kept, List(Result(1), Result(0)))
    assertEquals(p.at.map(_.title), Some("Statistics"))
    val details = p.at.toList.flatMap(_.parts).collect { case Part.Details(rows) => rows.map((k, v) => k -> v.text) }
    assertEquals(details, List(List("Played" -> "2", "Won" -> "50%", "Current streak" -> "0")))
  }

  test("the kept results' format, read back; anything unreadable skipped") {
    assertEquals(Result.read(Result.write(List(Result(4), Result(0)))), List(Result(4), Result(0)))
    assertEquals(Result.read("3 x 9 -1 2"), List(Result(3), Result(2)))
    assertEquals(Result.read(null), Nil)
  }

  test("the dictionary: the 5-letter words, the solver's pick a possible word") {
    assertEquals(Rules.words.size, 8506)
    assert(Rules.words.forall(_.matches("[a-z]{5}")))
    assert(Rules.suggest(Rules.words).exists(Rules.known))
  }
