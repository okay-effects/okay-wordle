package okay.wordle

import okay.business.scala3.*

/** A HANDLER, as this application names one: the effect `E` taken out of any program's row */
trait Handles[E[_]]:
  def apply[R[_], Rest[_], A](p: A ! R)(using Split[E, R, Rest]): A ! Rest

/** THE WORD OF A ROUND, as an effect: the program never picks it, it asks */
enum Secret[X]:
  case Draw() extends Secret[String]

object Secret:
  /** a random word of the dictionary, by `random` */
  def random(random: scala.util.Random = new scala.util.Random()): Handles[Secret] =
    Drawn(() => Rules.words(random.nextInt(Rules.words.size)))
  /** these words, round by round, again from the first after the last — for tests */
  def fixed(first: String, more: String*): Handles[Secret] =
    val all = (first +: more).toVector; var n = -1
    Drawn(() => { n += 1; all(n % all.size) })

final class Drawn(next: () => String) extends Handles[Secret]:
  def apply[R[_], Rest[_], A](p: A ! R)(using Split[Secret, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Secret, Rest, A, A](a => pure(a), [X] => (asked: Secret[X]) => asked match
      case Secret.Draw() => next()))

/** a round's end: solved in `moves`, or not (`moves` 0) */
final case class Result(moves: Int):
  def won: Boolean = moves > 0

object Result:
  /** the format the kept results share: "4 0 3", 0 a round lost; anything unreadable is skipped */
  def write(rs: List[Result]): String = rs.map(_.moves).mkString(" ")
  def read(text: String): List[Result] =
    if text == null then Nil else text.split(' ').toList.flatMap(_.toIntOption).filter(m => m >= 0 && m <= 6)
      .map(Result(_))

/** THE RESULTS OF THE ROUNDS PLAYED, as an effect. Where they are kept is a handler's */
enum Results[X]:
  case All() extends Results[List[Result]]
  case Add(r: Result) extends Results[Unit]

object Results:
  def inMemory(initial: List[Result] = Nil): InMemory = InMemory(initial)

final class InMemory(initial: List[Result]) extends Handles[Results]:
  var kept: List[Result] = initial
  def apply[R[_], Rest[_], A](p: A ! R)(using Split[Results, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Results, Rest, A, A](a => pure(a), [X] => (asked: Results[X]) => asked match
      case Results.All() => kept
      case Results.Add(r) => kept = kept :+ r))

/** THE MODEL, as an effect: given the moves so far, a logit for each letter at each of the five places. Which model
 * answers is a handler's — a toy here; a language model elsewhere, the program the same */
enum Model[X]:
  case Letters(moves: Vector[Rules.Move]) extends Model[Vector[Vector[Float]]]

object Model:
  /** THE TOY: 130 numbers — the log of each letter's count at each place in the dictionary — plus noise of 0.5 at
   * most; it never reads the colours, so alone it writes common letters, words or not */
  val table: Vector[Vector[Float]] = Vector.tabulate(5, 26)((i, l) =>
    math.log(Rules.words.count(_(i) - 'a' == l) + 1.0).toFloat)
  def toy(random: scala.util.Random = new scala.util.Random()): Handles[Model] = Toy(random)

final class Toy(random: scala.util.Random) extends Handles[Model]:
  def apply[R[_], Rest[_], A](p: A ! R)(using Split[Model, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Model, Rest, A, A](a => pure(a), [X] => (asked: Model[X]) => asked match
      case Model.Letters(_) => Model.table.map(_.map(_ + 0.5f * random.nextFloat()))))
