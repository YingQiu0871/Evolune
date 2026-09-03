# Evolune v1.4-A — Trust & Permission Foundation

## Baseline

- Source tree: `current/Evolune-v1.2` (legacy directory name; current build identity is v1.3.1).
- Git baseline: `main@f6a8dad5a7d4754fa7768672fd04ace4ea79af83`, tag `v1.3.1`.
- The nested source repository must be clean before implementation and release review.

## Scope

This slice establishes the trust and authorization foundation for v1.4:

1. Versioned first-launch onboarding with required Terms/Privacy and Medical/PK acknowledgements.
2. A local, re-openable disclosure surface reachable from Settings and About.
3. Contextual explanation immediately before notification, Health Connect, and Google Drive authorization flows.
4. Health Connect rationale intent routing to the disclosure surface.
5. Accessibility-friendly scrolling, readable copy, dark-mode compatibility, and width-independent layouts.

The guided feature tutorial (first plan, dose record, chart, widget, Wear, and backup walkthrough) is deferred to v1.4-B.

## Data boundary

Onboarding state uses its own DataStore and is deliberately excluded from `UserSettings`, Room, the backup codec, Health Connect state, and Wear state. Restoring Evolune data must not create Terms or medical disclosure acknowledgement on a different installation. Its one-time `initialized_for_v14` marker distinguishes a fresh install from an upgrade: an existing install skips the beginner tour but still starts with current disclosure acknowledgements unset.

The disclosure copy is Evolune-authored and must describe observable behavior: local Room/DataStore data, optional read-only Health Connect weight access, explicit encrypted Google Drive backup, phone-authoritative Wear state, model-estimated PK curves, and current update/backup network use.

## Authorization contract

Every flow in this slice follows:

`explain purpose and data boundary → Continue → existing platform/provider action`

`Not now` leaves the existing local feature state usable and does not invoke the platform/provider authorization UI. No new runtime permission or domain authority is introduced.

## Acceptance criteria

- Fresh installation cannot silently bypass the required disclosures.
- Existing installations see only the required disclosure review when a version changes, not the full beginner tutorial.
- The required Terms/Privacy checkbox is displayed after both canonical Terms and Privacy text.
- Settings and About expose the same canonical disclosure content.
- Declining optional notification, Health Connect, or Google Drive authorization does not delete plans or disable unrelated local features.
- Room remains medication-data authority; Health Connect remains optional read-weight only; Phone remains Wear authority; PK calculations and backup format remain unchanged.
- Tests cover state versioning, restore isolation, onboarding navigation, pre-authorization explanations, intent routing, increased font scale, and compact/expanded layouts.

## Provenance

No external app copy, source code, illustrations, layouts, or proprietary assets are imported. UX research may inform observable interaction patterns only. Existing PK attribution and scientific parameters remain unchanged.
