# Credits and licence

## Credits

All original design, code, and artwork are by **SuperMartijn642**. This fork exists only to keep the
Forge 1.12 branch alive for our own use, and claims no credit for the mod itself.

- Upstream repository: <https://github.com/SuperMartijn642/MovingElevators>
- Upstream README (including SuperMartijn642's own community links):
  <https://github.com/SuperMartijn642/MovingElevators/blob/1.15/README.md>
- Official downloads: <https://www.curseforge.com/minecraft/mc-mods/moving-elevators>

The fork's additions — the wall panel family, elevator doors, the call queue, banks, service modes
and the sound schemes — are Mica Technologies' work, built on top of his.

## Licence

The upstream project declares **all rights reserved**, and this fork inherits that. It is published
for use in Mica Technologies servers and modpacks.

**If you want to redistribute this mod or this fork, take it up with SuperMartijn642 first.**

## Changelogs

Two files, kept deliberately separate:

| File | Contents |
|---|---|
| [`changelog.md`](https://github.com/Mica-Technologies/LDMovingElevators/blob/forge-1.12/changelog.md) | **Upstream's** release notes, an unmodified mirror of SuperMartijn642's file. Never add fork entries here — upstream prepends to the top of it, so anything we put there conflicts on every merge. |
| [`CHANGELOG-FORK.md`](https://github.com/Mica-Technologies/LDMovingElevators/blob/forge-1.12/CHANGELOG-FORK.md) | **This fork's** changes, keyed by the `YYYY.MM.DD` release tags we publish. |

The newest heading in `changelog.md` doubles as the upstream release this fork sits on; the release
workflow parses it and prints it in each GitHub release body.

## Versioning

Release builds take their version from the git tag CI creates immediately before building
(`YYYY.MM.DD`, or `YYYY.MM.DD-pre.HHMM.<tz>+<sha>` for a pre-release). Local builds with no such tag
fall back to `mod_version` in `gradle.properties`, which records the upstream release this fork
currently sits on.

- **Releases** are versioned `YYYY.MM.DD` and are the ones to use.
- **Pre-releases** are cut automatically on every push. They are not guaranteed to be stable, and
  they are pruned after 90 days.

## Contributing

Source, issues and pull requests:
[github.com/Mica-Technologies/LDMovingElevators](https://github.com/Mica-Technologies/LDMovingElevators).
Developer notes live in `CLAUDE.md` in the repository root.

Every page of this wiki has an edit link in its top right corner.
