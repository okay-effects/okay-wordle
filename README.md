# okay-wordle

Wordle in a page, written on [okay-ui](https://github.com/okay-effects/okay-ui) — business screens as data on
algebraic effects — and drawn by [okay-faces](https://github.com/okay-effects/okay-faces)' Laminar face.

```sh
sbt appJS/web          # links the page into app/.js/target/web
open app/.js/target/web/index.html
```

Guess the 5-letter word in six tries; hard mode, a hint with the solver's pick, statistics kept in the page's storage.
Model plays: a tiny model (130 numbers) guesses on its own, and a mask built into a transformer's weights keeps every
guess to a word that fits the colours.
The whole application in the browser is one line (`InTheBrowser`): the program and the handlers of its effects.
