# Evolune Source Provenance

**Reviewed release commit:** `fbb9bafa1aaa605e6c59600203a177fcf957f74f`

**Review date:** 2026-08-14

**Purpose:** technical source-lineage and release-boundary record; not legal advice or a license grant.

Dependency and asset license text is maintained separately in [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).

## Evolune-maintained / apparently new work

Work added after the direct HRTTracker upstream baseline includes the Room v3 architecture, repository/domain boundaries, migration and recovery handling, Wear write pipeline, replay/idempotency policy, release verification documents, and later UI/runtime integration. Git history identifies the contributing commits and authors. The root MIT statement covers Evolune-owned work; it does not purport to relicense separately sourced material.

The existing maintainer line in `LICENSE` is useful and sufficient. Adding another maintainer copyright line is `UNNECESSARY`; no ownership transfer from upstream contributors is implied.

## Direct MIT upstream - HRTTracker

Evolune is an independent continuation and substantial re-engineering of [`NaiveTomcat/HRTTracker`](https://github.com/NaiveTomcat/HRTTracker).

- Direct upstream baseline: `043fb2b2eae3b72b1af718b46bcba797ec6fe8dd` (`upstream/master`).
- Reachability evidence: that commit is an ancestor of the reviewed release commit.
- Upstream license evidence: the exact upstream `LICENSE` is MIT and contains `Copyright (c) 2026 Yitong Dang`.
- Preservation: the inherited copyright and MIT permission notice remains in the root `LICENSE`.

## Modified/inherited upstream material

The application source, launcher artwork, notification icon, and other files that already existed at the upstream baseline have subsequently been renamed, moved, or substantially modified. They remain inherited MIT material plus later Evolune modifications. Git history, not a claim of from-scratch authorship, is the authoritative per-file record.

## Inspiration/reference projects

[`SmirnovaOyama/Oyama-s-HRT-Tracker`](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker) is cited as product/scientific inspiration. The reviewed release tree contains brief reference comments and a documentation link; the Batch 9A review found no copied image, chart, prose block, or separately derived source identified from that project. This is a citation/factual-reference boundary, not a code-license claim.

## PK implementation provenance and pending permission

Evolune's current estradiol pharmacokinetic implementation materially traces to work published by LaoZhong-Mihari in [`HRT-Recorder-PKcomponent-Test`](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test).

- Attribution is preserved.
- No explicit repository-level license or permission grant was located during the completed provenance review.
- Explicit permission has been requested from the original author; the response remains pending.
- This provenance record is not itself a license grant and does not classify the PK implementation as MIT.
- The project owner has elected to proceed with v1.0 while accurately disclosing this pending risk.

`PK_PERMISSION_STATUS = PERMISSION_REQUESTED_RESPONSE_PENDING`

`PK_PROVENANCE_RISK = OWNER_ACCEPTED_PENDING_PERMISSION`

`PK_RELEASE_POLICY = PUBLIC_RELEASE_ALLOWED_WITH_DISCLOSED_PENDING_PROVENANCE_RISK`

## Third-party assets

- `app/src/main/java/io/github/yuninggu/evolune/ui/icons/TablerIcons.kt` contains a shipped Tabler Icons vector. Its full Paweł Kuna MIT notice is preserved in source and in `THIRD_PARTY_NOTICES.md`.
- Phone launcher and notification artwork trace through Git history to the direct MIT HRTTracker baseline.
- Wear tile icon and tile preview were added by the Evolune maintainer in commit `959aa93fcd5fa1f2fddda252c3f28b8fc1cba52b`; they are repository-native vector resources.
- The release APKs contain no bundled fonts, screenshots, promotional images, or sample-media payloads.

## Scientific/reference sources

Scientific/project references, including the Oyama citation and public data-format compatibility references, are used as citations, factual context, or interoperability targets unless a more specific lineage entry says otherwise. No tracked release material marked CC BY-NC-SA, and no copied CC BY-NC-SA prose, image, chart, or derived code, was identified in this review. Citation alone does not alter Evolune's source license.

## Archived/non-production reference material

Featherline/`feiwuliyong` GPLv3 migration snapshots, patches, and dedicated assets are absent from the reviewed release tree. Historical discussion remains in older design/provenance records as evidence. The material itself is retained only in the protected local evidence root and the local tree ref:

`refs/codex/turn-diffs/checkpoints/d22fd9bea88e7ca976e5ec988d9ad0418f270cc72b3480e8df82b02c995006c7/f705e54dfe1428b053535ebdbc4abfcc95dea271ebdac9512389e9864a9dc79f/1786291873064/16efa636-a14e-42a0-88f9-86dacf5f0e49`

Read-only reachability analysis found no substantive Featherline/GPL blob reachable from release HEAD, `main`, existing tags, or origin's public remote-tracking branches. A generic seven-byte `.gitignore` blob is byte-identical across the trees; it is not Featherline source or a protected asset. The internal ref and full object database must not be published. See the Batch 9A publication-boundary report for the permitted publication pattern.

## Release controls

1. Publish only the explicitly reviewed release branch and a tag created from its approved sealing commit.
2. Do not use `git push --all`, `git push --mirror`, unrestricted `git bundle --all`, or distribute the complete `.git` object database.
3. Ship `THIRD_PARTY_NOTICES.md` with binary release materials or provide it as release documentation accompanying those binaries.
4. Preserve the PK attribution and pending-permission wording until a separately authorized permission-closure review changes it.
5. Keep the calibration branch separate from v1.0; it is not merged into the reviewed release commit.
