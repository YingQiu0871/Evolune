# Evolune v1.4-B — Guided Feature Tutorial

## Scope

This slice adds a re-openable, six-step feature tutorial after the mandatory
v1.4-A disclosures on a genuinely fresh installation:

1. Create a medication plan.
2. Record an actual dose.
3. Read the PK estimate on the home screen.
4. Add and configure the Phone Widget.
5. View the phone-derived summary on Wear.
6. Use optional encrypted Google Drive backup.

Every step is skippable and can advance without using its optional action. The
tutorial is also available as a separate Settings entry; the existing
“Guide, privacy and permissions” entry remains the canonical disclosure and
permission guidance surface.

## Interaction and authority boundary

The plan and dose actions open the existing editors only. They never create a
MedicationPlan or DoseEvent automatically. The PK action opens the existing
home screen, Widget and Wear steps provide instructions without invoking a
platform picker or requesting permissions, and the backup action opens the
existing backup surface where its contextual authorization contract still
applies.

Room remains medication-data authority, Phone remains Wear authority, and the
tutorial state is device-local UI state. Tutorial completion is not included
in legal acknowledgement state and is not included in backup or restore data.

## Launch and exit behavior

Fresh installation state sets a local `featureTutorialAutoLaunchPending` flag.
After the required Terms/Privacy and Medical/PK acknowledgements, the tutorial
becomes the start destination. Finish, Skip, system Back, or the top-bar Up
action clears the flag and returns to Home. A v1.3 upgrade skips the beginner
tutorial; a manually opened tutorial returns to its previous Settings screen.

Explicit intent destinations continue to take precedence over tutorial
auto-launch, so Health Connect disclosure routing remains unchanged.

## Acceptance criteria

- Six steps appear in the documented order and remain readable when scrolled.
- Finish, Skip, and each optional CTA are independently testable.
- Plan and dose CTAs reuse the existing root editor overlays.
- PK and backup CTAs reuse existing routes without duplicating authority.
- Widget and Wear pages perform no platform or permission action.
- Fresh installs auto-launch once; upgrades do not; Settings can reopen it.
- Legal acknowledgement, backup/restore, Room, Widget, and Wear behavior are
  unchanged.
