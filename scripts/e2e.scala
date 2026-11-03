//> using scala 3.3.8
//> using dep com.microsoft.playwright:playwright:1.63.0

import com.microsoft.playwright.{BrowserType, Page, Playwright}
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Path
import scala.jdk.CollectionConverters.*

/** THE PAGE END TO END in the installed Chromium (`$CHROME`, else Playwright's chromium-1234; never a download): a
 * round played and given up, the statistics, a refused guess; pictures into the directory given (default /tmp).
 * `scala-cli run scripts/e2e.scala -- app/.js/target/web/index.html [pictures dir]`. Exit 1 on the first wrong
 * thing */
@main def e2e(page: String, pictures: String = "/tmp"): Unit =
  try run(page, pictures)
  catch case e: Throwable => { e.printStackTrace(); sys.exit(1) }

def run(page: String, pictures: String): Unit =
  val exe = sys.env.getOrElse("CHROME", sys.props("user.home") +
    "/Library/Caches/ms-playwright/chromium-1234/chrome-mac-arm64/" +
    "Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing")
  def fail(why: String): Nothing = { System.err.println("FAIL: " + why); sys.exit(1) }
  // an exception not caught ends the script: Playwright's threads would keep it running
  Thread.setDefaultUncaughtExceptionHandler((_, e) => { e.printStackTrace(); sys.exit(1) })
  val pw = Playwright.create(Playwright.CreateOptions().setEnv(Map("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" -> "1").asJava))
  val browser = pw.chromium().launch(BrowserType.LaunchOptions().setExecutablePath(Path.of(exe)))
  val p = browser.newContext(com.microsoft.playwright.Browser.NewContextOptions().setViewportSize(480, 1000)).newPage()
  val errors = scala.collection.mutable.ArrayBuffer.empty[String]
  p.onPageError(e => errors += e)
  p.setDefaultTimeout(15000)
  println("opening " + page)
  p.navigate(Path.of(page).toAbsolutePath.toUri.toString)
  def button(name: String) = p.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name).setExact(true))
  def shot(name: String) = p.screenshot(Page.ScreenshotOptions().setPath(Path.of(pictures, name)).setFullPage(true))
  def text = p.locator("#app").textContent()
  button("New game").click()
  p.getByLabel("Guess").fill("slate")
  button("Guess").click()
  p.waitForFunction("() => document.querySelectorAll('rect[class*=\"tile-\"]:not([class*=\"tile-empty\"])')" +
    ".length === 5")
  if !text.contains("Guess 2 of 6") then fail("not the second guess: " + text)
  p.getByLabel("Guess").fill("qqqqq")
  button("Guess").click()
  p.waitForFunction("() => document.body.textContent.includes('not in the dictionary')")
  button("Hint").click()
  p.waitForFunction("() => document.body.textContent.includes('still possible')")
  shot("wordle-round.png")
  button("Give up").click()
  p.waitForFunction("() => document.body.textContent.includes('The word was')")
  val answer = "The word was ([A-Z]{5})".r.findFirstMatchIn(text).fold(fail("no word told"))(_.group(1))
  shot("wordle-end.png")
  button("Back to the menu").click()
  button("Statistics").click()
  p.waitForFunction("() => document.body.textContent.includes('Played')")
  if !"Played\\s*1(?!\\d)".r.findFirstIn(text).isDefined then fail("one round not counted: " + text.takeRight(300))
  shot("wordle-statistics.png")
  if errors.nonEmpty then fail("page errors: " + errors.mkString("; "))
  println(s"e2e: ok (the word was $answer); pictures in $pictures")
  browser.close(); pw.close()
