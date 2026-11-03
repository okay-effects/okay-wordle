package okay.wordle

/** THE RULES, pure: the dictionary, the colours of a guess, the words still possible, the solver's pick */
object Rules:
  val words: Vector[String] = Dictionary.words
  val known: Set[String] = words.toSet
  val tries = 6

  enum Colour:
    case Grey, Yellow, Green

  /** the colours Wordle gives: green in place; yellow only as many times as the answer has the letter left after its
   * greens; grey otherwise */
  def colours(guess: String, answer: String): Vector[Colour] =
    val c = Array.fill(5)(Colour.Grey)
    val left = Array.fill(26)(0)
    for i <- 0 until 5 do if guess(i) == answer(i) then c(i) = Colour.Green else left(answer(i) - 'a') += 1
    for i <- 0 until 5 if c(i) == Colour.Grey do
      val l = guess(i) - 'a'
      if left(l) > 0 then { c(i) = Colour.Yellow; left(l) -= 1 }
    c.toVector

  final case class Move(guess: String, colours: Vector[Colour]):
    def solved: Boolean = colours.forall(_ == Colour.Green)
    def emoji: String = colours.map { case Colour.Green => "🟩"; case Colour.Yellow => "🟨"; case _ => "⬜" }.mkString

  /** the words that would have given every move its colours */
  def possible(moves: Seq[Move]): Vector[String] =
    words.filter(w => moves.forall(m => colours(m.guess, w) == m.colours))

  /** the solver's pick (hard mode): the possible word with the most common letters, by place and by presence */
  def suggest(cands: Vector[String]): Option[String] =
    val pos = Array.ofDim[Int](5, 26); val has = Array.fill(26)(0)
    for w <- cands do
      for i <- 0 until 5 do pos(i)(w(i) - 'a') += 1
      for c <- w.distinct do has(c - 'a') += 1
    cands.maxByOption(w => (0 until 5).map(i => pos(i)(w(i) - 'a')).sum + w.distinct.map(c => has(c - 'a')).sum)

  /** each letter's best colour so far, for the keyboard: none when it was not tried */
  def keys(moves: Seq[Move]): Map[Char, Colour] =
    moves.flatMap(m => m.guess.zip(m.colours)).groupMapReduce(_._1)(_._2)((a, b) => if a.ordinal >= b.ordinal then a
      else b)
