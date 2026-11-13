# Tasks: No Back pill on grown-up screens

- [X] T001 Remove `BackKeyPill`, `GrownUpTags.BACK`, `ParentCodePageTags.BACK` and the pill from the gate, the menu
  and Add time; drop `backLabel` and `gateBackLabel` (`kids/ui/*.kt`)
- [X] T002 Remove `kids_gate_back_to` / `kids_gate_back` in English and Spanish
- [X] T003 Robolectric tests: the gate and the menu show no "Back to Lucy"; Back still leaves
- [X] T004 DESIGN "Behaviour": gate and grown-up pages have no Back pill; superseded notes in specs 019 and 021
- [X] T005 `nix develop -c ./gradlew :app:testDebugUnitTest :app:assembleDebug`, pre-commit on changed files
