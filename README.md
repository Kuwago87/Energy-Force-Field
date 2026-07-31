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
   Field Tools** menu with three items:

   | Item | Tag | Does |
   |---|---|---|
   | Lightning Rod | Create / Delete | Select corners, create, and delete fields |
   | End Crystal | On / Off | Toggle the nearest field you control |
   | Book | - | Opens a written book listing every field you own |

   Click (or shift-click) an item to take it, same as pulling something out
   of a chest - the slot refills a moment later so you can grab all three in
   one visit instead of reopening the menu each time.

2. With the **Create/Delete rod** in hand: left-click cycles between the two
   corners. First left-click sets corner 1, the next left-click sets corner
   2 and *immediately* creates the field (auto-named after you, e.g.
   `tony-1`, `tony-2`, ...), and the click after that starts a fresh corner
   1 - so you can keep stamping out fields with left-click alone.
   Right-click deletes the nearest field of yours - it asks for a second
   right-click within 5 seconds to confirm.

3. With the **On/Off crystal** in hand: right-click anywhere near a field
   you control to toggle it. It doesn't need to be an exact block - it
   finds the closest field within range (8 blocks by default), so it works
   whether the field is currently raised (solid) or lowered (open air).

4. Open the **book** any time to see every field you own: name, world,
   corners, volume, state, and whether it's linked to redstone. It's
   generated fresh each time, so it's always current.

While raised, a field is filled with invisible `BARRIER` blocks: nothing
can walk, fly, or shoot through it, it can't be broken or blown up, and it
periodically crackles and hums. Lowering it restores every block to
exactly what was captured when the field was created - so it's safe to
draw one over an existing door, window, or decorated arch.

## Ownership

Every field created via the rod (GUI or `/forcefield create`) is owned by
the player who made it. The rod and crystal only let you delete/toggle
your own fields unless you have `forcefield.admin`, which can manage
everyone's. The book only ever shows your own fields.

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
| `forcefield.use` | op | `/eff_tools`, the rod/crystal, and managing your own zones |
| `forcefield.admin` | op | Create, remove, link/unlink, and reload *any* zone (chat commands), plus toggle/delete zones owned by others |

## Configuration (`config.yml`)

- `create-delete-rod-material` / `on-off-crystal-material` - items used for the two tools
- `toggle-tool-range` - how far the crystal/rod-delete will reach to find a field
- `delete-confirm-window-ms` - how long a delete confirmation stays valid
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
