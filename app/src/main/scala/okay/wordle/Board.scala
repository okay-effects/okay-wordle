package okay.wordle

import okay.business.scala3.*
import okay.wordle.Rules.{Colour, Move}

/** THE BOARD AS A PICTURE: six rows of five tiles, the keyboard under them. Each shape is marked with its colour's
 * word (`tile-green`, `key-grey`, ...); how a mark looks is the skin's (`Look.css`). A label follows its tile, so the
 * skin can colour a letter by the tile under it */
object Board:
  val tile = 56; val gap = 6; val width = 5 * tile + 4 * gap
  val keyW = 27; val keyH = 42; val keyGap = 3
  val rows = Vector("qwertyuiop", "asdfghjkl", "zxcvbnm")
  val top = Rules.tries * (tile + gap) + 14
  val height = top + rows.size * (keyH + keyGap)

  private def word(c: Colour) = c match
    case Colour.Green => "green"
    case Colour.Yellow => "yellow"
    case Colour.Grey => "grey"

  def picture(moves: Seq[Move]): Part =
    val tiles = (0 until Rules.tries).flatMap { r =>
      (0 until 5).flatMap { i =>
        val x = i * (tile + gap); val y = r * (tile + gap)
        moves.lift(r) match
          case None => List(Part.Shape.Box(x, y, tile, tile, "tile-empty", ""))
          case Some(m) =>
            val letter = m.guess(i).toUpper.toString
            List(Part.Shape.Box(x, y, tile, tile, s"tile-${word(m.colours(i))}", s"$letter ${word(m.colours(i))}"),
              Part.Shape.Label(x + tile / 2.0, y + tile / 2.0 + 10, letter, "middle"))
      }
    }
    val tried = Rules.keys(moves)
    val keyboard = rows.zipWithIndex.flatMap { (row, r) =>
      val shift = (width - row.length * (keyW + keyGap) + keyGap) / 2.0
      row.zipWithIndex.flatMap { (c, i) =>
        val x = shift + i * (keyW + keyGap); val y = top + r * (keyH + keyGap)
        val mark = tried.get(c).fold("key")(k => s"key-${word(k)}")
        List(Part.Shape.Box(x, y, keyW, keyH, mark, c.toUpper.toString),
          Part.Shape.Label(x + keyW / 2.0, y + keyH / 2.0 + 7, c.toUpper.toString, "middle"))
      }
    }
    val text = if moves.isEmpty then "an empty board" else moves.map(m => s"${m.guess} ${m.emoji}").mkString("\n")
    Part.picture(width, height, text)((tiles ++ keyboard)*)
