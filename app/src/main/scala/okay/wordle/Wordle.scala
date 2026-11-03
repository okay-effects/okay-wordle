package okay.wordle

import okay.business.scala3.*
import okay.wordle.Rules.Move

/**
 * WORDLE, in okay-ui's words alone: a menu, the round's screen (the board, a guess, a hint, give up), the end of a
 * round, the statistics. What it knows of the world it asks: the word of `Secret`, the rounds of `Results`, the
 * person of `Ui`. Which handlers answer is the line that runs it.
 */
object Wordle:
  type App[X] = (Ui + Secret + Results)[X]

  def main[R[_]: Has[Ui]: Has[Secret]: Has[Results]]: Unit ! R =
    menu[Int, R]("Wordle", "New game" -> 1, "Hard mode" -> 2, "Statistics" -> 3, "How to play" -> 4).flatMap {
      case 1 => round[R](hard = false).andThen(defer(main[R]))
      case 2 => round[R](hard = true).andThen(defer(main[R]))
      case 3 => statistics[R].andThen(defer(main[R]))
      case _ => inform[R]("How to play", howTo).andThen(defer(main[R]))
    }

  val howTo: String = "Guess the hidden 5-letter word in 6 tries. After each guess the tiles show how close you " +
    "are: green — the letter is in the word, in that place; yellow — in the word, elsewhere; grey — not in the " +
    "word (a letter guessed twice is yellow only as many times as the word has it). In hard mode every guess must " +
    "fit the colours so far. Hint shows how many words are still possible and the solver's pick."

  enum Act:
    case Guess(word: String)
    case Hint
    case GiveUp

  /** a guess: five letters, a word of the dictionary — and in hard mode, a word that fits every colour so far */
  def form(hard: Boolean, moves: Seq[Move]): Form[String] =
    val guess = Field.text("Guess").check("five letters, a to z")(_.toLowerCase.matches("[a-z]{5}"))
      .check("not in the dictionary")(w => Rules.known(w.toLowerCase))
    Form.one(if hard then guess.check("hard mode: it must fit the colours so far")(w =>
      Rules.possible(moves).contains(w.toLowerCase)) else guess)

  /** one round, from drawing its word to its end: the result kept */
  def round[R[_]: Has[Ui]: Has[Secret]: Has[Results]](hard: Boolean): Unit ! R =
    perform[Secret](Secret.Draw()).flatMap(answer => turn[R](answer, hard, Vector.empty, None))

  def turn[R[_]: Has[Ui]: Has[Secret]: Has[Results]](answer: String, hard: Boolean, moves: Vector[Move],
      note: Option[String]): Unit ! R =
    val f = form(hard, moves)
    val status = s"Guess ${moves.size + 1} of ${Rules.tries}" + (if hard then " · hard mode" else "")
    show(Screen[Act]("Wordle")((Seq(Board.picture(moves), Part.text(status)) ++ note.map(Part.info) :+ f)*)(
      Button.submit("Guess", f)(Act.Guess(_)), Button("Hint")(Act.Hint), Button("Give up", Style.Danger)(Act.GiveUp)))
      .flatMap {
        case Act.Hint =>
          val cands = Rules.possible(moves)
          val pick = Rules.suggest(cands).fold("")(w => s"; the solver would try ${w.toUpperCase}")
          turn[R](answer, hard, moves, Some(s"${count(cands.size)} still possible$pick"))
        case Act.GiveUp => end[R](answer, moves, solved = false)
        case Act.Guess(g) =>
          val m = Move(g.toLowerCase, Rules.colours(g.toLowerCase, answer))
          val now = moves :+ m
          if m.solved then end[R](answer, now, solved = true)
          else if now.size == Rules.tries then end[R](answer, now, solved = false)
          else defer(turn[R](answer, hard, now, None))
      }

  /** the round's end: its result kept, the board with the answer */
  def end[R[_]: Has[Ui]: Has[Results]](answer: String, moves: Vector[Move], solved: Boolean): Unit ! R =
    perform[Results](Results.Add(Result(if solved then moves.size else 0))).andThen {
      val said = if solved then Part.info(s"Solved in ${moves.size} of ${Rules.tries}!")
        else Part.warning(s"The word was ${answer.toUpperCase}.")
      show(Screen[Unit](if solved then "Solved" else "Not this time")(Board.picture(moves), said,
        Part.text(moves.map(_.emoji).mkString("\n")))(Button("Back to the menu", Style.Primary)(())))
    }

  def statistics[R[_]: Has[Ui]: Has[Results]]: Unit ! R =
    perform[Results](Results.All()).flatMap { rs =>
      val won = rs.filter(_.won)
      val streak = rs.reverse.takeWhile(_.won).size
      val share = if rs.isEmpty then "—" else s"${won.size * 100 / rs.size}%"
      val table = Table((1 to Rules.tries).toList)(Column.text("Solved in")(n => s"$n"),
        Column.text("Rounds")(n => won.count(_.moves == n).toString)).keyedBy(_.toString)
      show(Screen[Unit]("Statistics")(Part.details("Played" -> rs.size.toString, "Won" -> share,
        "Current streak" -> streak.toString), table)(Button("Back", Style.Primary)(())))
    }

  def count(n: Int): String = if n == 1 then "1 word" else s"$n words"
