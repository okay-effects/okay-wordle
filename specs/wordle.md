# okay-wordle — Wordle in a page, on okay-ui and okay-faces (spec, 2026-11-03)

The game of `okay-inlay/experiments/Wordle.sc` and `play/Wordle.sc`, as a web application: okay-ui's screens as data
(`Screen`, `Part`, `Form`, `Field`, `Table`, `Button`), drawn by okay-faces' Laminar face in the browser.

## The rule

The data model is effects; every implementation is a handler; the browser runs the application in ONE line.

| effect | operation | answered with | handlers |
|---|---|---|---|
| `Secret` | `Draw()` | the word of a round | `Secret.random()` (the browser), `Secret.fixed(w, ...)` (tests) |
| `Results` | `All()`, `Add(r)` | the rounds played, `Unit` | `Results.inMemory()` (tests), `InLocalStorage(key)` (the browser) |
| `Ui` | `Show(screen)` | the button pressed | okay-ui's `steps` |
| `Model` | `Letters(moves)` | a logit per letter at each of the 5 places | `Model.toy()` (the browser, tests with a seed) |

```scala
Page.mount(OnFaces.program(steps(Model.toy()(InLocalStorage("wordle-results")(Secret.random()(
  Wordle.main[App]))))), skin)
```

## The game

- The dictionary: the 8,506 five-letter lowercase words of /usr/share/dict/words (web2), kept as `app/words.txt`,
  made a Scala value by the build (`Dictionary.words`). Many are rare; a list of common answers is a later step.
- The colours (`Rules.colours`): green in place; yellow only as many times as the answer has the letter left after its
  greens; grey otherwise.
- The menu: New game, Hard mode, Model plays, Statistics, How to play. No Quit (the operator's rule).
- A round: the board (six rows of five tiles, the keyboard under them, each letter its best colour so far) as
  `Part.Picture`, its shapes marked `tile-green`, `key-grey`, ...; "Guess n of 6"; a form of one field, Guess — five
  letters a to z, a word of the dictionary, and in hard mode a word that fits every colour so far (a refused guess
  stays in its field with its problem). Hint: how many words are still possible and the solver's pick
  (`Rules.suggest`, as `Wordle.sc`'s). Give up.
- The end: solved in n, or the word told; the board and the emoji rows; the result kept (`Result(n)`, 0 a loss).
- Statistics: played, the share won, the current streak, the rounds by the number of guesses.
- The look: okay-ui's `Skin.calm` in Wordle's green, plus the marks' colours; a tile's letter follows its tile, so the
  stylesheet colours a key's letter by the key (`.okay-mark-key + .okay-picture-label`).

## Model plays (operator, 2026-11-04)

A model guesses on its own; a mask built into a transformer's weights keeps its guesses to words that fit.

- The model is the effect `Model`; its handler here is a TOY, very small and in the page (the operator: tiny, or on
  Cloudflare's side): 130 numbers, the log of each letter's count at each place in the dictionary, plus noise of 0.5
  at most. It never reads the colours. Another handler (a language model, on a Worker) would leave the program as it
  is.
- The mask is `Inlay`, okay-inlay's `experiments/Wordle.sc` steps 2–3 in float32: a word a position, its letters
  one-hot (5 × 26), a counting MLP (≥ j copies of a letter), a move compiled to literals, the head "allowed words"
  at −λ·(literals broken), the letters written so far more green literals of the same head, its value the next
  letter's one-hot; a letter allowed when the head gives it more than 0.
- Each turn, "Next move": the model's logits; its guess unmasked (the highest letter at each place) and masked (the
  highest allowed letter); the masked one is played. The screen shows what it wanted and why that was stopped (not a
  word, against the colours, or the same), what it played, the words still possible. The end tallies what the mask
  stopped. Model rounds are not kept in the statistics.
- Checked (`ModelSuite`): the head's allowed words equal `Rules.possible` move by move (repeated letters included);
  masked, every guess fits — unmasked, the toy invents words or breaks the colours; the mode played through by its
  buttons. In Chromium, a round of 4 moves takes ~190 ms (`scripts/e2e.scala`).

## Checks

- `sbt appJVM/test` — `WordleSuite`: the whole application by `Play`, the word fixed, results in memory: a round
  solved, refused guesses, hard mode, the hint, six misses, give up, the statistics, the kept format.
- `sbt appJS/web` links the page into `app/.js/target/web`; `scala-cli run scripts/e2e.scala --
  app/.js/target/web/index.html` plays it in the installed Chromium (a guess, a refused one, the hint, give up, the
  statistics) and writes pictures to /tmp.
