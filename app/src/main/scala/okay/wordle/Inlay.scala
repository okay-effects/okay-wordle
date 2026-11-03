package okay.wordle

import okay.wordle.Rules.{Colour, Move}

/**
 * THE HINT FILTER AND THE MOVE MASK AS A TRANSFORMER'S WEIGHTS — okay-inlay's experiments/Wordle.sc, steps 2 and 3,
 * in float32. A word is a position; its letters one-hot (5 x 26); an MLP counts letters (a ReLU ladder: >= j copies
 * of L); a move compiles to literals, a query whose dot product with a word's features is the number of literals the
 * word breaks; the head "allowed words" scores -lambda * that, so its softmax is uniform on the words that break
 * nothing and exactly 0 elsewhere. The mask: the letters written so far are more green literals of the same head,
 * its value the next letter's one-hot; a letter is allowed when the head's output for it is above 0.
 */
object Inlay:
  val lambda = 250f
  private val relu = (x: Float) => math.max(0f, x)
  private val words = Rules.words
  private val N = words.size
  // the features: [x: 130 | ge_1..ge_5 of each letter: 130 | one: 1]
  val D = 261
  private def geAt(j: Int, l: Int) = 130 + (j - 1) * 26 + l
  private val ONE = 260

  /** a word's features: the one-hot letters, then the counting MLP (a linear count, then the ladder) */
  def features(w: String): Array[Float] =
    val f = Array.fill(D)(0f)
    for i <- 0 until 5 do f(i * 26 + w(i) - 'a') = 1f
    val count = Array.tabulate(26)(l => (0 until 5).map(i => f(i * 26 + l)).sum)
    for j <- 1 to 5; l <- 0 until 26 do f(geAt(j, l)) = relu(count(l) - j + 1) - relu(count(l) - j)
    f(ONE) = 1f
    f
  /** the keys: every word's features, made once */
  lazy val keys: Array[Array[Float]] = words.map(features).toArray

  type Query = Map[Int, Float]
  private def plus(a: Query, b: Query): Query =
    (a.keySet ++ b.keySet).map(k => k -> (a.getOrElse(k, 0f) + b.getOrElse(k, 0f))).toMap

  /** a move's literals: green, the letter here (1 - x); yellow or grey, not here (x); the letter's count at least n
   * (1 - ge_n), or exactly n once a grey of it was seen (+ ge_{n+1}) */
  def compile(m: Move): Query =
    val q = scala.collection.mutable.Map.empty[Int, Float].withDefaultValue(0f)
    def add(k: Int, v: Float) = q(k) = q(k) + v
    for i <- 0 until 5 do
      val at = i * 26 + m.guess(i) - 'a'
      if m.colours(i) == Colour.Green then { add(ONE, 1f); add(at, -1f) } else add(at, 1f)
    for c <- m.guess.distinct do
      val idx = (0 until 5).filter(m.guess(_) == c)
      val n = idx.count(m.colours(_) != Colour.Grey)
      if n > 0 then { add(ONE, 1f); add(geAt(n, c - 'a'), -1f) }
      if idx.exists(m.colours(_) == Colour.Grey) then add(geAt(n + 1, c - 'a'), 1f)
    q.filter(_._2 != 0f).toMap
  def query(moves: Seq[Move]): Query = moves.map(compile).foldLeft(Map.empty[Int, Float])(plus)

  /** the head: softmax over the words of -lambda * the literals each breaks (the product skips the query's zeros) */
  def head(q: Query): Array[Float] =
    val s = Array.fill(N)(0f)
    for (k, c) <- q; w <- 0 until N do s(w) += c * keys(w)(k)
    val mx = s.map(-lambda * _).max
    val e = s.map(v => math.exp((-lambda * v - mx).toDouble).toFloat)
    val z = e.sum
    e.map(_ / z)
  /** the words the head allows */
  def allowed(moves: Seq[Move]): Vector[String] =
    val w = head(query(moves)); (0 until N).filter(w(_) > 0f).map(words).toVector

  /** the mask for the next letter after `prefix`: the head with the prefix as green literals, its value the letter */
  def mask(q: Query, prefix: String): Vector[Boolean] =
    val greens = prefix.indices.map(i => Map(ONE -> 1f, (i * 26 + prefix(i) - 'a') -> -1f)).foldLeft(q)(plus)
    val w = head(greens)
    val out = Array.fill(26)(0f)
    val k = prefix.length
    for i <- 0 until N if w(i) > 0f do for l <- 0 until 26 do out(l) += w(i) * keys(i)(k * 26 + l)
    out.toVector.map(_ > 0f)

  /** a word written letter by letter: at each place the allowed letter with the highest logit */
  def decode(logits: Vector[Vector[Float]], allowed: String => Vector[Boolean]): String =
    (0 until 5).foldLeft("") { (p, k) =>
      val a = allowed(p)
      p + ('a' + (0 until 26).filter(a).maxBy(logits(k))).toChar
    }
  def free(logits: Vector[Vector[Float]]): String = decode(logits, _ => Vector.fill(26)(true))
  def masked(logits: Vector[Vector[Float]], moves: Seq[Move]): String =
    val q = query(moves); decode(logits, p => mask(q, p))
