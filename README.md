# EFF - Energy Force Field

A Paper plugin that turns any doorway, wall gap, or open area into a Star
Trek style energy shield, controlled through an in-game GUI. Raise it and
it becomes a solid, invisible barrier that crackles with `sculk_charge_pop`
particles and hums; lower it and the space opens back up exactly as it was
before.

## Requirements

- Paper (or a Paper fork) for Minecraft / Paper API **26.2** (what used to
  be called "1.26.2")
- JDK 25 to build
- Maven 3.9+

## Building

```
mvn clean package
```

The compiled plugin jar will be at `target/EFF-1.0.0.jar`. Drop it into
your server's `plugins/` folder and (re)start the server.

## Quick start (GUI)

1. `/eff_tools` (aliases: `/eff`, `/efftools`) - opens the **Energy Force
   Field Tools** menu:

   | Item | Tag | Does |
   |---|---|---|
   | Lightning Rod | Create / Delete | Select corners, create, and delete fields |
   | End Crystal | Remote On / Off | Toggle whichever field you're looking at, within a 10 block radius |
   | Book | - | Right-click while held to open the "My Energy Force Fields" list GUI |
   | Enchanted Book | Admin only | Right-click while held to open **every** field on the server - only appears here if you have `forcefield.admin` |

   Click (or shift-click) an item to take it, same as pulling something out
   of a chest - the slot refills a moment later so you can grab everything
   in one visit instead of reopening the menu each time.

2. With the **Create/Delete rod** in hand: left-click cycles between the two
   corners. First left-click sets corner 1, the next left-click sets corner
   2 and *immediately* creates the field (auto-named after you, e.g.
   `tony-1`, `tony-2`, ...), and the click after that starts a fresh corner
   1 - so you can keep stamping out fields with left-click alone. Creating a
   field also hands you 2 **Force Field Lecterns** linked to it - see
   [Lecterns](#lecterns-the-physical-onoff-switch) below. Right-click
   deletes the nearest field of yours - it asks for a second right-click
   within 5 seconds to confirm.

3. With the **Remote On/Off** (End Crystal) in hand: right-click to toggle
   whichever field you're actually looking at, within a 10 block radius by
   default (`crystal-remote-range`). This is a genuine
   line-of-sight check (a ray cast from your eyes), not just "nearest field"
   - so if two fields are both in range but only one is in front of you,
   right-clicking always hits the one you're aiming at. Only works on
   fields you own (or any field, if you're an admin) - unlike lecterns, the
   remote ignores a field's public/private setting.

4. The **book** is a pickable item, same as the rod - take it from the
   tools menu, then right-click while holding it (anytime, not just from
   the menu) to open the **My Energy Force Fields** list: one icon (a
   Netherite Nautilus Armor by default) per field you own, with paper
   Previous/Next buttons if you have more than 45. Click a field to open
   its management menu:

   | Item | Tag | Does |
   |---|---|---|
   | Name Tag | Rename | Prompts you to type a new name for that field in chat |
   | Compass | Location | Shows the field's world/coordinates and points your compass at it |
   | Oak Door | Access: Public/Private | Toggles whether anyone can flip this field with its lecterns, or just you/admins |
   | Lever | Remote On / Off | Toggles that field, right from the menu |
   | Lectern | Get Replacement Lecterns | Gives you 2 more lecterns linked to this field |
   | Barrier | Delete | Deletes that field immediately |
   | Paper | Back to list | Returns to the field list |

   Clicking **Rename** closes the menu and asks you to type the new name
   in chat (or `cancel`) within 30 seconds by default. Names must be 1-32
   characters, letters/numbers/`-`/`_` only, and not already used by
   another field.

   Everything here is generated fresh each time you open it, so it's
   always current.

5. Players with `forcefield.admin` also get the **Enchanted Book** ("All
   Energy Force Fields"), also pickable and right-click-to-open. It lists
   *every* field on the server, not just your own, with each icon's lore
   showing its owner. Opening a field from here gives the same buttons as
   above, plus one more:

   | Item | Tag | Does |
   |---|---|---|
   | Player Head | Change Owner | Prompts you to type an online player's exact username in chat to transfer that field to them |

   Both the Rename and Change Owner prompts work the same way: click,
   type your answer in chat (or `cancel`), and it applies within 30
   seconds by default. Change Owner only accepts a currently online
   player's exact username.

## Lecterns (the physical on/off switch)

Instead of a portable remote, each field is toggled with **Force Field
Lecterns** - real Lectern blocks you place yourself, e.g. one on each side
of a doorway.

- **Right-click** a placed one to raise/lower the field it's linked to
  (subject to public/private access, below).
- **Double-left-click** one (two left-clicks in quick succession, within
  `lectern-double-click-window-ms` - 400ms by default) to open an **edit
  menu** for its field, with the same buttons as the book's field detail
  menu (Rename, Location, Public/Private, Remote On/Off, Get Replacement
  Lecterns, Delete field), plus a **Remove This Lectern** button that
  breaks just the one you clicked, leaving the field itself untouched.
  Opening this menu always requires being the field's owner or an admin,
  even if the field is public - toggling and managing are different things.
- A single left-click by itself does nothing (it's just waiting to see if a
  second one follows). Creative mode is an exception: a single left-click
  always instantly breaks any block in creative, lecterns included, so the
  double-click gesture only really applies in survival.

Setup and maintenance:

- Creating a field (via the rod) automatically gives you `lecterns-per-field`
  lecterns (2 by default) linked to it. Place them wherever makes sense -
  they don't need to touch the field itself.
- Every field starts **private**: only its owner (or an admin) can toggle it,
  from a lectern or anywhere else. Flip the **Oak Door** ("Access") button
  to make it **public**, and anyone can toggle it from its lecterns too -
  handy for a shared airlock or a door the whole crew can use.
- Lecterns aren't specially protected - they can be broken like any other
  block (by you, another player, an explosion, ...). If one is destroyed, or
  you just want to move it, use **Get Replacement Lecterns** (in the field
  detail GUI, or a surviving lectern's own edit menu) for two fresh ones.
- Lecterns are linked to their field by a stable internal id, not its name -
  renaming a field never breaks an already-placed lectern. (Lecterns placed
  by a version of EFF from before this existed only knew the field by name;
  the very next time one of those is clicked, EFF quietly upgrades it to the
  id-based link automatically - unless the field has *already* been renamed
  since that lectern was placed, in which case the old name is gone for good
  and that lectern can't be recovered. Get a replacement for it instead.)
- A lectern only ever does one thing: control the single field it's linked
  to. Placing an ordinary, un-tagged lectern anywhere else on your server
  still works exactly like vanilla.

While raised, a field is filled with invisible `BARRIER` blocks: nothing
can walk, fly, or shoot through it, it can't be broken or blown up, and it
periodically crackles and hums. Lowering it restores every block to
exactly what was captured when the field was created - so it's safe to
draw one over an existing door, window, or decorated arch.

## Ownership

Every field created via the rod (GUI or `/forcefield create`) is owned by
the player who made it. The rod, the remote crystal, and the field
list/detail GUI only let you delete/toggle your own fields unless you have
`forcefield.admin`, which can manage everyone's. Lecterns are the one
exception - they follow the same rule unless a field is marked Public (see
[Lecterns](#lecterns-the-physical-onoff-switch)). The regular book's list
only ever shows your own fields; admins can see and manage every field (and
reassign its owner) from the admin book instead.

## Chat commands (for admins / precise control)

All commands are under `/forcefield` (aliases: `/ff`, `/shield`) - handy
for naming a field explicitly, or managing fields you don't own.

| Command | Effect |
|---|---|
| `wand` | Get the Create/Delete rod |
| `create <name> [confirm]` | Create a zone from your current rod selection, with an explicit name |
| `remove <name>` | Delete a zone (lowers it first if raised) |
| `toggle <name> [on\|off]` | Raise/lower a zone, or flip its current state |
| `list` | List all zones and their state |
| `info <name>` | Show a zone's world, corners, volume, state, owner, and redstone link |
| `link <name>` | Next block you left-click with the rod becomes this zone's redstone trigger |
| `unlink <name>` | Remove a zone's redstone trigger |
| `reload` | Reload config.yml and fields.yml from disk |

Selections over `max-volume-without-confirm` blocks (5000 by default)
require `/forcefield create <name> confirm`; the rod's quick-create is
always capped at this size with no override, as a safety net.

## Redstone control

`/forcefield link <name>` puts you into link mode; left-click a lever,
button, or any other redstone-emitting block with the rod, and the zone
will automatically raise when it's powered and lower when it isn't -
handy for a sci-fi control panel next to the door.

## Permissions

| Node | Default | Grants |
|---|---|---|
| `forcefield.use` | op | `/eff_tools`, the rod/remote, and managing your own zones |
| `forcefield.admin` | op | Create, remove, link/unlink, and reload *any* zone (chat commands), plus toggle/delete zones owned by others |

## Configuration (`config.yml`)

- `create-delete-rod-material` / `on-off-crystal-material` / `fields-book-material` / `admin-fields-book-material` - items used for the rod, remote crystal, pickable book, and admin book
- `crystal-remote-range` - how far (in blocks) the On/Off remote's line-of-sight check reaches
- `lecterns-per-field` - how many Force Field Lecterns you get when creating a field, and how many the "Get Replacement Lecterns" button hands out (2 by default; set to 0 to stop giving them out automatically)
- `lectern-double-click-window-ms` - how quickly two left-clicks on the same lectern must land to count as a double-click and open its edit menu
- `fields-list-icon-material` - icon used for each field entry in the list GUI (defaults to `NETHERITE_NAUTILUS_ARMOR`)
- `toggle-tool-range` - how far the rod's right-click delete will reach to find a field
- `delete-confirm-window-ms` - how long a delete confirmation stays valid
- `rename-window-ms` - how long you have to type a new name in chat after clicking Rename before it's dropped and treated as normal chat
- `owner-change-window-ms` - same, but for the admin book's Change Owner prompt
- `max-volume-without-confirm` - safety cap described above
- `ambient-interval-ticks` / `ambient-particle-count` / `ambient-sound-radius` - how often, how dense, and how far the shimmer/hum effect on raised fields runs
- `edge-outline-interval-ticks` - how often the top/bottom edge of a raised field is re-traced; kept short by default so the boundary stays continuously visible instead of flickering
- `resist-feedback-cooldown-ms` - throttle for the "the field resists you" feedback
- `effects.*` - particle/sound names for activation, deactivation, ambient shimmer, and resist feedback (defaults to the crackling `SCULK_CHARGE_POP` particle throughout). Each one is validated on startup and falls back to a safe default (with a console warning) if your server doesn't recognize it - the plugin will never fail to load because of a bad effect name.
- `messages.*` - every player-facing message, with `&`-style colour codes and `%placeholder%` substitution

## Notes

- Raising a field only turns its currently *empty/passable* blocks into
  barriers - solid blocks caught inside the selection (a door frame, the
  floor, the ceiling, decorations, ...) are left completely untouched, so
  they stay visible and don't get overwritten.
- Barrier blocks are invisible in survival by design (that's the Star
  Trek look) - in creative mode players can see their outline with F3+B
  enabled, same as any other barrier block.
- A zone's "shields down" state is captured once, at creation time.
  Toggling only ever swaps between that captured state and a wall of
  barriers - it never re-captures, so the baseline stays exactly what you
  designed.
- Zones persist in `plugins/EFF/fields.yml`, including owner, which
  blocks to restore, and any redstone link, so they survive restarts.

## Metrics (bStats)

EFF reports anonymous usage stats via [bStats](https://bstats.org/plugin/bukkit/EFF/33044)
(plugin id `33044`) - things like player count, server version, and Java
version, the same as most Bukkit/Paper plugins. No personal data is
collected. This can be turned off server-wide in `plugins/bStats/config.yml`
(`enabled: false`) without affecting anything else in EFF.
