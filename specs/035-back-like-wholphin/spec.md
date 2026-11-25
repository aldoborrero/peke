# Feature Specification: Back on pages with the sidebar works as in Wholphin

**Feature Branch**: `035-back-like-wholphin`

**Created**: 2026-11-26

**Status**: Implemented

**Supersedes**: spec 024 (the "Leave Peke?" question and Back closing the sidebar on the home) and spec 017's
Back-closes-the-sidebar rule. 017's other parts stay: focus returning to where it was when it comes back from the
sidebar, and the larger play icon.

**Input**: the owner's decision of 2026-11-26, after reading Google's Android TV guidance: Back on Peke's pages with
the sidebar behaves exactly as in upstream Wholphin v1.1.1 (`ui/nav/NavDrawer.kt`: "If the user presses back while on
the home page, open the nav drawer, another back press will quit the app").

Before: on the home, Back with the sidebar closed asked "Leave Peke?" and, with it open, closed it (spec 024); on
other pages, Back with the sidebar open closed it (spec 017). Both gate the exit or make Back a menu toggle.

## Google's guidance

- [Navigation on TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv) and
  [TV navigation](https://developer.android.com/training/tv/start/navigation): "Avoid exit gating. Let users exit out
  of the app without confirmation"; "Ensure that the back button isn't gated by confirmation screens or part of an
  infinite loop".
- Left navigation apps: Back should "activate the left side menu and focus on the currently active menu item";
  "Ideally, pressing the back button exits out of the app".
- [Controllers](https://developer.android.com/training/tv/start/controllers): "The Back button must never act as a
  toggle. For example, don't use it to both open and close a menu."
- Quality criterion TV-DB: "Back button presses lead back to the Android TV home screen."

## Behaviour

| Where | Back does |
|---|---|
| Home, sidebar closed | opens the sidebar, focus on Home (the current item) |
| Home, sidebar open | leaves the app (the system finishes it, as in Wholphin) |
| Other page with the sidebar (Search, Favourites, Live, series/season details…), sidebar closed | previous page |
| Other page, sidebar open | previous page, and the sidebar closes with it; Back never just closes the sidebar |
| Player, grown-up pages, parent gate, setup, picker | unchanged |

The sidebar still opens with Left and closes with Right or by choosing an item.

## Requirements

- **FR-001** On the kids home with the sidebar closed, Back opens it with focus on the current item (Home).
- **FR-002** On the kids home with the sidebar open, Back goes on to the system, which quits: no question, no parent
  code (constitution IV, 2.1.2).
- **FR-003** On other pages with the sidebar, Back goes back a page whether or not the sidebar is open; an open
  sidebar closes in the same press, so it isn't left open on the page Back goes to (the sidebar's state is shared by
  every page).
- **FR-004** Back never only closes the sidebar, and never both opens and closes it.
- **FR-005** A page's own Back handling (the parent gate, time's up) still wins where it applies.
- **FR-006** "Leave Peke?" (`KidsLeaveDialog`) and its strings are removed.

## Decisions

- **D1. Close and pass on.** On other pages with the sidebar open, the handler closes the sidebar, steps aside and
  dispatches the same Back again, so whatever handles Back next (the page list going back) does it. Wholphin doesn't
  need this step only because there the next page doesn't reopen the drawer; in Peke every page shares the drawer
  state, and the page Back goes to would open with the sidebar still open.
- **D2. No Back-to-close on the home either.** With the sidebar open on the home, Back is the second Back of
  Wholphin's pair: it quits.

## Success Criteria

- Unit tests cover each row of the table: `NavDrawerBackTest` (the rules) and `DrawerBackHandlerTest`
  (Compose/Robolectric, driving the real `DrawerBackHandler`).
- On a TV: Back on the home opens the sidebar on Home; Back again returns to the Android TV home screen with no
  question. On Search with the sidebar open, Back returns to the home with the sidebar closed.
