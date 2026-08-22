# Evolune v1.2 — Risk Register

**Date:** 2026-08-22
**Scope:** Health Connect weight + provider-independent backup/restore + Google Drive app-data provider.

Severity: **P0** catastrophic/data loss/security; **P1** release blocker; **P2** significant; **P3** minor.
Likelihood: Low / Medium / High.

| ID | Risk | Sev | Likelihood | Mitigation / gate | Owner stage |
|---|---|---:|---|---|---|
| R-01 | Local worktree/remote baseline divergence causes v1.2 to start from the wrong parent | P1 | Medium | Live remote `main` verified at `cc1f963...`; fetch before branch creation and assert HEAD parent | S0 |
| R-02 | Protected `D:\Evolune` root is accidentally modified | P1 | Low | Work only in approved clean worktree; pre/post status inventory; never use protected root as build/output target | All |
| R-03 | GitHub integration cannot create branch/refs | P2 | High (current) | Do not write `main`; create branch locally or grant integration refs permission; S0 artifacts remain repo-ready until resolved | S0 |
| R-04 | Health Connect alpha dependency introduces unstable behavior | P2 | Medium | Pin stable `connect-client:1.1.0`; alpha upgrade requires separate review | HC1 |
| R-05 | Health Connect provider missing/outdated on API 31–33 | P2 | Medium | Check SDK status every operation; explicit update-required UI; core app remains independent | HC1/HC3 |
| R-06 | Permission revoked outside Evolune after UI assumed it was granted | P2 | Medium | Check granted permissions immediately before every read; map denial/revocation to recoverable state | HC1/HC3 |
| R-07 | Evolune requests excessive health permissions (history/background/write) | P1 | Low | Manifest/test allowlist only `READ_WEIGHT`; static gate rejects forbidden permissions | HC1 |
| R-08 | External weight silently changes PK inputs | P1 | Medium | Preview first; explicit **Use this weight** confirmation; Health Connect never writes PK state directly | HC2 |
| R-09 | Adopted/manual/imported weight is persisted but current HRTViewModel keeps stale constructor value | P1 | High (current gap) | Make local authoritative weight reactive and part of simulation trigger; regression test all weight mutation paths | HC2 |
| R-10 | No/stale/invalid external weight confuses user or overwrites a good local value | P2 | Medium | No-record is non-error; show timestamp/freshness; validate range; never auto-adopt | HC2 |
| R-11 | Future request for historical Health Connect data silently expands privacy scope | P2 | Medium | v1.2 explicitly bans `READ_HEALTH_DATA_HISTORY`; new scope requires roadmap/design approval | HC |
| R-12 | Mahiro JSON v1 is mistakenly used as complete cloud backup | P1 | Medium | New backup schema lives separately; tests assert plans/slots/settings coverage; Mahiro v1 regression unchanged | B1 |
| R-13 | Backup format omits a canonical local data category and creates silent loss on restore | P0 | Medium | Explicit coverage inventory + golden complete fixtures; version bump for future fields; release gate checks snapshot completeness | B1 |
| R-14 | Backup DTOs serialize Room entities directly and become coupled to schema internals | P2 | Medium | Stable backup DTO + mapper boundary; provider-independent codec; Room schema can evolve independently | B1 |
| R-15 | User forgets client-side backup passphrase and cannot restore | P1 | Medium | Clear onboarding/warning; confirm passphrase; optional local secure cache does not replace recovery secret; no false recovery promise | B1/B4 |
| R-16 | Weak KDF/nonce handling compromises encrypted health backup | P0 | Low–Medium | AES-GCM; cryptographically random nonce/salt; versioned KDF params; tested vectors; security review before release | B1 |
| R-17 | Tampered/corrupt backup reaches local mutation | P0 | Low | Authenticate/decrypt then validate complete snapshot before preview/confirmation; no mutation on any validation error | B1/B2 |
| R-18 | Restore implemented as delete + loop inserts leaves partially restored Room state | P0 | Medium | Dedicated snapshot restore component using one Room transaction | B2 |
| R-19 | Room commits but DataStore fails, leaving cross-store inconsistent state | P0 | Medium | Durable pre-restore journal + compensating rollback/recovery; verify postconditions before success | B2 |
| R-20 | Process death during restore leaves ambiguous state | P0 | Low–Medium | `noBackupFilesDir` restore journal/state machine; startup recovery before normal writes | B2 |
| R-21 | Restore triggers reminders/widgets/Wear before persistence completes | P1 | Medium | Persistence-before-side-effects; publish/reschedule only after verified commit | B2 |
| R-22 | Future backup version is interpreted with old semantics | P1 | Medium | Magic + explicit format/payload versions; reject unsupported future major version before mutation | B1/B2 |
| R-23 | Google auth flow conflates app authentication with Drive authorization | P2 | Medium | Credential Manager for account identity only; `AuthorizationClient` for Drive scope; request at point of use | B3 |
| R-24 | Broad Drive permission exposes unrelated user files | P1 | Low | Allowlist only `drive.appdata`; test OAuth scope request; no `drive`, `drive.readonly`, `drive.file` | B3 |
| R-25 | Drive token expires or user revokes access | P2 | High over lifetime | Treat access token as short-lived; call authorization flow again; no token-as-permanent-state assumption | B3 |
| R-26 | User authorizes a different Google account than expected | P2 | Medium | Show selected account identity when available; explicit account change/re-auth UX; never merge generations across accounts implicitly | B3/B4 |
| R-27 | New backup upload overwrites/deletes the only good generation | P0 | Low–Medium | Immutable generation first, verify creation, then prune; keep latest 3; cleanup failure non-fatal | B3 |
| R-28 | Network failure yields a local “success” even though remote backup is incomplete | P1 | Medium | Success only after Drive create/upload response is verified; local snapshot creation alone is not cloud success | B3/B4 |
| R-29 | Hidden `appDataFolder` is mistaken for Android system backup and Auto Backup rules are changed | P1 | Low | Document separation; leave backup/data-extraction rules unchanged; regression static test | B3 |
| R-30 | Background sync is introduced and causes battery/privacy surprises | P2 | Medium | v1.2 manual foreground operations only; no WorkManager sync; any automation is future separately approved scope | B3/B4 |
| R-31 | Google/Health integrations become hard dependencies and break offline/core use | P1 | Medium | Optional feature boundaries, fakes, graceful unavailable states; core medication/PK features require neither provider | All |
| R-32 | Existing v1.1 migration/repository/widget/Wear behavior regresses | P1 | Medium | Full existing automated suite + upgrade test + real-device acceptance before release | QA |
| R-33 | v1.1 tags/releases are moved or rebuilt during v1.2 work | P1 | Low | Treat published tag/release as immutable; verify before release | All |

## Top release blockers

The following risks are explicitly **release-blocking** until mitigated with evidence:

1. R-09 — stale PK after weight change.
2. R-13 — incomplete backup coverage.
3. R-16/R-17 — backup confidentiality/integrity failure.
4. R-18/R-19/R-20 — partial or interrupted restore.
5. R-24 — overbroad Drive scope.
6. R-27/R-28 — false/unsafe cloud-backup success.

## Risk review cadence

- Re-review after HC1, HC2, B1, B2, B3, and before release candidate.
- Any new schema, permission, OAuth scope, background worker, or external-data write path automatically reopens S0 risk review for the affected track.
