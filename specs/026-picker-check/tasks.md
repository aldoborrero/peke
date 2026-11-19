# Tasks: Kid picker check mark on top, Down to Continue

**Input**: [plan.md](plan.md), [spec.md](spec.md)

- [X] T001 Badge outside the clipped surface, on the edge, above border and ring, scaled with focus
  (`kids/setup/KidAvatar.kt`)
- [X] T002 `KidsChoice`: Down from a kid to Continue, else Check again (`kids/setup/SetupFlow.kt`)
- [X] T003 [P] Tests: `KidsChoiceTest` (Down with and without a picked kid, check and lock on the edge outside the
  clip, no badge unpicked, badge scaled with focus), `KidBadgeRenderTest` (the check covers the focus ring)
