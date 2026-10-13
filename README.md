# NBTEditor-Gradients

A client-side Fabric addon for [NBT Editor](https://modrinth.com/mod/nbt-editor): a gradient editor and colour codes of
any format, right where the name and the lore of an item are edited.

## What it adds

**Commands**, on the item in hand:

- `/iname <text>`
- `/ilore add <text>`, `/ilore set <line> <text>`, `/ilore remove <line>`

The text may hold colour codes of any of these formats, mixed freely: MiniMessage (`<gradient:#ff0000:#0000ff>`,
`<rainbow>`, `<b>`...), `&#rrggbb`, `#rrggbb`, JSON, `§x§r§r§g§g§b§b`, `&x&r&r&g&g&b&b`, `<#rrggbb>`,
`[COLOR=#rrggbb]`. The text is not italic unless the code says so.

**In the display screen** (`/factory display`):

- a button on each text field that turns the colour codes typed in it into coloured text;
- a gradient editor docked at the bottom: draggable stops, RGB / HSL / OKLAB / OKLCH blending, letters per colour, one
  gradient per word or one for the whole selection, the colours of a text picked back up;
- several selections at once, with Ctrl held;
- presets, with a list to choose from; they are exported as files named after them and imported in another instance;
- the size of the item next to the save button;
- Ctrl+Z / Ctrl+Y through everything that was done to the item, until the world is left;
- a ring on the colour picker that shows where its colour is.

**In every text field of NBT Editor**: Home and End stop at the ends of the line the cursor is on (Ctrl+Home and
Ctrl+End go to the ends of the text), and Up and Down keep their column.

**An item that is edited** goes to the history of `/get lostitem`, as it was and as it becomes.

## Versions

One jar per version of Minecraft, in the [releases](../../releases):

| Minecraft | NBT Editor |
|---|---|
| 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.6, 1.21.1, 1.21.4 | [2.0.3](https://modrinth.com/mod/nbt-editor) (mega12345mega) |
| 1.21.10, 1.21.11 | 2.0.9, the [port](https://modrinth.com/mod/nbteditor-port) (NebulaJinxed) |
| 26.1.2, 26.2, 26.3 | 2.0.11, the same port |

Fabric API and NBT Editor are required.

## Building

The root is the source, for Minecraft 1.21.11. `port/port.sh` writes the source of every other version from it into
`versions/<minecraft>/src`: nothing there is edited by hand.

```bash
./gradlew build                                   # 1.21.11
bash port/port.sh                                 # the source of the other versions
cd versions/1.20.1 && ./gradlew build             # one of them
```

JDK 21 builds everything up to 1.21.11, JDK 25 the 26.x versions. For 1.17.1, 1.18.2 and 1.19.4, `port/port.sh` also
makes a `mappings.jar` (it needs `JAVA_HOME`) from the Mojang mappings in the cache of Loom, which are there once
Gradle has been run in that version: see the comments of the script.

The tests drive the real game: `./gradlew runClientGameTest` where Fabric has a client gametest API (1.21.10 and
later), `bash port/devcheck.sh <minecraft>` for the older versions, `bash port/testall.sh` for all of them.

## Credits and licence

NBT Editor is the work of [mega12345mega](https://github.com/mega12345mega/NBT-Editor), ported to the newer versions
of Minecraft by NebulaJinxed. Its jars in `libs/` are there to build against; they are under the MIT licence, which
each of them carries.

NBTEditor-Gradients is under the [MIT licence](LICENSE).
