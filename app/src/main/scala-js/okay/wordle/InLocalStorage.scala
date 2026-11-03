package okay.wordle

import okay.business.scala3.*
import org.scalajs.dom

/** THE RESULTS IN THE PAGE'S STORAGE: `window.localStorage[key]`, read on each question, rewritten on each round —
 * kept across reloads; nothing there, or something unreadable, is no rounds */
final class InLocalStorage(key: String) extends Handles[Results]:
  def kept: List[Result] = Result.read(dom.window.localStorage.getItem(key))
  def apply[R[_], Rest[_], A](p: A ! R)(using Split[Results, R, Rest]): A ! Rest =
    handle(p, Handler.Tail[Results, Rest, A, A](a => pure(a), [X] => (asked: Results[X]) => asked match
      case Results.All() => kept
      case Results.Add(r) => dom.window.localStorage.setItem(key, Result.write(kept :+ r))))
