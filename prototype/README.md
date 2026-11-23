# Cyclus prototype

Clickable prototype (phone frame) from the design phase: the **visual and copy reference** for both apps. Its translation table is the string source for the apps (`tools/strings/export.mjs`), and its engines produce the golden test vectors (`tools/golden/`).

- Live link (private, share via the page's Share menu): https://claude.ai/artifact/R7vg5CVUJEQkEdiNEbpcM2
- Local: `python3 -m http.server 8765` in this folder, then open http://127.0.0.1:8765/

German first with a language selector (Deutsch / English) in the panel and in Settings.

Runs fully in the page on sample data dated around 2026-11-22. The period model
(`predict`) and fertility model (`fertility`, `trail`) implement DESIGN_SPEC §11.5
and §11.11 in simplified form so that logging changes estimates live. They are the
reference for the shared Kotlin engines (checked by golden vectors), not production code.
