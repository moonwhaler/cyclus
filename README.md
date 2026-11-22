# Cyclus

Private, local-first cycle tracker. **German first**, iOS first, Android with full parity.
"Dein Zyklus, verstanden. Nie hochgeladen." No servers, no accounts, no tracking. All intelligence runs on the device.

## Documents
| File | What it is |
|---|---|
| [`docs/DESIGN_SPEC.md`](docs/DESIGN_SPEC.md) | **Main spec** (product, UX/UI, threat model, engines, delivery). Start with §0 (decisions D1–D10) |
| [`docs/ANDROID_PLAN.md`](docs/ANDROID_PLAN.md) | Android design & plan: parity rules, KMP architecture, Material 3 Expressive mapping, Health Connect, Play |
| [`docs/REVIEW.md`](docs/REVIEW.md) | Sceptic review of the first plan (12 MAJOR / 24 MINOR), all resolved in the spec (§0.3) |
| [`docs/PLAN.md`](docs/PLAN.md) | First plan, superseded, kept for history |
| [`prototype/`](prototype/) | Clickable HTML prototype (DE/EN) with live period, fertility, pattern and outlook engines. **Visual and behavioural reference for both apps** |

## Prototype
```sh
cd prototype && python3 -m http.server 8765   # open http://127.0.0.1:8765/
```
Published (private) link: see `prototype/README.md`.

## Status (2026-11-22)
- Spec v1.0 + amendments:
  - fertility in v1;
  - German first;
  - no Apple Watch on hand;
  - „Deine Muster“ + „Ausblick“ in v1.
- Android plan drafted. D10 (Kotlin Multiplatform shared core) awaits confirmation.
- Open owner decisions:
  - legal entity + organization developer accounts (Apple, Google);
  - regulatory specialist;
  - clinician reviewer.
- Next engineering step: port the prototype's JS engines to Kotlin (`shared/`) with golden test vectors (ANDROID_PLAN §11).
