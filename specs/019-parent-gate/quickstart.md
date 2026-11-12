# Quickstart: checking the parent gate page

## Tests

```sh
nix develop -c ./gradlew :app:testDebugUnitTest \
  --tests 'dev.aldoborrero.peke.kids.KidsParentGateViewModelTest' \
  --tests 'dev.aldoborrero.peke.kids.ParentCodePageTest' \
  --tests 'dev.aldoborrero.peke.kids.ParentLockHintTest'
nix develop -c pre-commit run --files <changed files>
```

## Previews

`ParentCodePage.kt` has previews for the four palettes, both code types and the three errors; open them in Android
Studio and compare with board 7 and S5.

## On the TV (owner, who knows the code)

1. Grown-ups from the sidebar: the page fills the screen, "Back to {Kid}", cross or pad per the household's type.
2. A wrong code: red-ish dots and "Wrong code" on the page, no toast; type again.
3. Back with keys typed: back to the home.
4. From the picker: the pill says "Back".
5. Settings › Household › change the parent code: same page, "Enter the current parent code".
6. Each theme in Settings › This TV.
