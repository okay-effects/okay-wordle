package okay.wordle

import okay.business.faces.{OnFaces, Skin}
import okay.business.laminar.Page
import okay.business.scala3.steps
import okay.wordle.Wordle.App

/** WORDLE IN THE BROWSER: `sbt appJS/web`, then open app/.js/target/web/index.html — the word drawn at random, the
 * rounds in the page's storage, the screens on okay-faces' Laminar face. The whole application is the line below */
object InTheBrowser:
  /** okay-ui's calm look in Wordle's green, and the board's marks: the tiles, their letters, the keys */
  val skin: Skin =
    val calm = Skin.calm(accent = "#538d4e", deep = "#2f5130")
    calm.copy(css = calm.css + """
      |body > footer .bar { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px; }
      |body > footer .more { margin-left: auto; }
      |body .okay-picture-frame { max-width: 360px; margin: 0 auto; }
      |body .okay-picture-shape { rx: 4px; stroke-width: 2px; }
      |body .okay-mark-tile-empty { fill: var(--okay-surface); stroke: var(--okay-border); }
      |body .okay-mark-tile-green, body .okay-mark-key-green { fill: #6aaa64; stroke: #6aaa64; }
      |body .okay-mark-tile-yellow, body .okay-mark-key-yellow { fill: #c9b458; stroke: #c9b458; }
      |body .okay-mark-tile-grey, body .okay-mark-key-grey { fill: #787c7e; stroke: #787c7e; }
      |body .okay-mark-key { fill: var(--okay-border); stroke: var(--okay-border); }
      |body .okay-picture-label { font: 700 26px var(--okay-font); fill: #fff; pointer-events: none; }
      |body .okay-mark-key + .okay-picture-label { fill: var(--okay-fg); }
      |body [class*="okay-mark-key"] + .okay-picture-label { font-size: 15px; }
      |""".stripMargin)

  def main(args: Array[String]): Unit =
    Page.mount(OnFaces.program(steps(Model.toy()(InLocalStorage("wordle-results")(Secret.random()(
      Wordle.main[App]))))), skin)
