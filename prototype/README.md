# Cyclus prototype

Clickable iPhone prototype for user interviews (design spec week 1–2).

- Live link (private, share via the page's Share menu): https://claude.ai/artifact/R7vg5CVUJEQkEdiNEbpcM2
- Local: `python3 -m http.server 8765` in this folder, then open http://127.0.0.1:8765/

Runs fully in the page on sample data dated around 2026-11-22. The period model
(`predict`) and fertility model (`fertility`, `trail`) implement DESIGN_SPEC §11.5
and §11.11 in simplified form so that logging changes estimates live. They are a
reference for the Swift implementation, not production code.
