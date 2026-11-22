# Golden vectors

The prototype (`prototype/index.html`) is the behavioural source of truth for the engines. `export.mjs` slices its
engine code, runs it in Node (no dependencies, no DOM) and writes input/output pairs to `shared/golden/`:

| File | Cases |
|---|---|
| `named.json` | The prototype scenarios (`empty`, `day12`, `expected`, `period`, `hbc`, `conceive`, `conceive2ww`) + fertility shown in track mode |
| `sweep.json` | `day12` and `conceive`, with today moved forward 1…35 days without new logs (all status transitions) |
| `fuzz.json` | 60 seeded random histories: irregular cycles, gaps, overrides, pauses, all log fields |

`shared/src/commonTest/.../GoldenTest.kt` checks the Kotlin engines against them on the JVM (`./gradlew :shared:jvmTest`)
and on Android (`./gradlew :shared:connectedAndroidDeviceTest`). Integers, enums and text keys must match exactly,
doubles to a relative 1e-9.

Regenerate after changing an engine in the prototype:
```sh
node tools/golden/export.mjs
```
Text comes out as `{k, a}` (translation key + arguments), item/tag names as stable keys and formatted numbers as
`{num, d}`, so the vectors check what is said, not how a language renders it.
