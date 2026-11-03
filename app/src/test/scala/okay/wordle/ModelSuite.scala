package okay.wordle

import okay.business.scala3.*
import okay.wordle.Rules.Move
import okay.wordle.Wordle.App

/** MODEL PLAYS: the inlaid head equals the rules' words still possible; the masked guess always fits; unmasked, the
 * toy breaks the colours or invents words; the mode played through by its buttons */
class ModelSuite extends munit.FunSuite:
  val rnd = new scala.util.Random(2026)
  def randomGame(answer: String, n: Int): Vector[Move] =
    Vector.fill(n)(Rules.words(rnd.nextInt(Rules.words.size))).map(g => Move(g, Rules.colours(g, answer)))

  test("the head's allowed words equal the words still possible, move by move") {
    for a <- Seq("crane", "abbey", "eerie", "llama") ++ Seq.fill(6)(Rules.words(rnd.nextInt(Rules.words.size))) do
      val moves = randomGame(a, 4)
      for k <- 1 to moves.size do
        assertEquals(Inlay.allowed(moves.take(k)), Rules.possible(moves.take(k)), s"$a after ${moves.take(k)}")
  }

  test("masked, the toy's guess is always a word that fits; unmasked, it is often neither") {
    val model = Model.table
    var invented = 0; var against = 0
    for g <- 0 until 30 do
      val r = new scala.util.Random(g)
      val a = Rules.words(r.nextInt(Rules.words.size))
      var moves = Vector.empty[Move]
      while moves.size < 3 && !moves.lastOption.exists(_.solved) do
        val logits = model.map(_.map(_ + 0.5f * r.nextFloat()))
        val free = Inlay.free(logits)
        if !Rules.known(free) then invented += 1
        else if !Rules.possible(moves).contains(free) then against += 1
        val played = Inlay.masked(logits, moves)
        assert(Rules.possible(moves).contains(played), s"$played after $moves")
        moves :+= Move(played, Rules.colours(played, a))
    assert(invented + against > 0, "the toy alone does break the rules: the mask has work to do")
  }

  test("model plays, by its buttons: every guess a word that fits, to the end; nothing kept in the statistics") {
    val results = Results.inMemory()
    def run(acts: Act*) = Play(steps(Model.toy(new scala.util.Random(3))(results(Secret.fixed("crane")(
      Wordle.main[App])))), acts*)
    // "Next move" until the round ends: at most six
    val p = (1 to 6).iterator.map(k => run((Act.press("Model plays") +: List.fill(k)(Act.press("Next move")))*))
      .find(_.at.exists(_.title.startsWith("The model"))).get
    assert(p.at.exists(s => s.title.startsWith("The model")), p.seen.toString)
    val rows = p.at.toList.flatMap(_.parts).collect { case Part.Picture(_, _, _, t) => t }.head.split('\n').toVector
    val moves = rows.map(_.take(5)).map(g => Move(g, Rules.colours(g, "crane")))
    for k <- moves.indices do assert(Rules.possible(moves.take(k)).contains(moves(k).guess), s"${moves(k)}")
    assertEquals(results.kept, Nil)
  }
