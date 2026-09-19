v1.7.3 navigation-jank hotfix — README carry-forward
====================================================

Source: ebb5a198a75557cc9ba905b8353aeaa40b76b114:readme.md
Expected blob: fad2bf2db9490a95653afcb739a637087cbf05a8

Method: `git checkout ebb5a198 -- readme.md` (exact blob restore; NOT a
cherry-pick, so the stale-main parent chain was not imported). An initial
PowerShell `>` redirection attempt corrupted the file (UTF-16) and was discarded
before commit; the final content is byte-identical to the source blob.

Verification:
  git hash-object readme.md = fad2bf2db9490a95653afcb739a637087cbf05a8 (match)
  git diff --name-only (staged) = readme.md only
Commit: a6b5d712b2070abb5d056e6f698196ea6c315904
  "docs: carry bilingual README onto v1.7.2 baseline" (parent f6b9134)

The README carry-forward is NOT part of the navigation implementation.diff
(that diff is computed from a6b5d71).
