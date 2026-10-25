# CinePlay — cinema screens for your server

Frame a rectangle on a wall with a command, and play a Bilibili video on it.
**Playback starts when players walk close and stops when they leave.**

A screen is **geometry, not blocks**: nothing is placed in the world, nothing goes into chunk data —
just four corner coordinates. Actual playback is handled by the client mod **CineDuct**; this plugin
only decides *what* plays, *where*, and *from when*.

> **Requires a client-side mod**: [CineDuct](https://github.com/hxwi1/CineDuct-API)
> (NeoForge 1.21.1, Windows x64). Players without it are unaffected — they simply don't see the screen.

---

## How it differs from map-art / entity screens

- **Zero footprint**: a screen is only coordinates — stick it on a wall, float it in the air, hang it
  over water, even watch it through glass
- **Automatic start/stop**: playback begins within 48 blocks and stops past 64 blocks
  (configurable per screen)
- **Per-screen permissions**: whoever creates a screen owns it, and "control playback" can be granted
  separately from "delete the screen"
- **Server-authoritative state**: playlist, progress and volume live on the server, so progress
  survives relogging and switching machines

---

## Creating a screen

```
/cineplay create                 enter selection mode (auto-cancels after 5 minutes)
right-click twice with a wooden hoe   the two opposite corners
/cineplay confirm <name>         confirm and name it
/cineplay add <name> <url>       queue a video — starts immediately
```

A third right-click re-selects the first corner. While selecting, only **you** see the particles
(corner markers plus a dashed guide line).

Geometry rules:

| Rule | Behaviour |
| --- | --- |
| Plane | Taken from the **block face you clicked**, not your view direction — where you stand doesn't matter |
| Orientation | Vertical, facing the side you clicked from |
| Size | Counted in blocks you covered (clicking block 10 and block 12 gives a width of 3, inclusive) |
| Offset | Floats **0.05 blocks** off the wall to avoid z-fighting with block faces |
| Aspect | Videos are **letterboxed** when their ratio differs from the screen — never stretched |

---

## Commands

Everything is screen-centric. There is deliberately **no "play for one specific player"** mode —
a screen belongs to the world, so whoever walks close sees it.

### Menu (recommended)

```
/cineplay menu
```

A clickable chat menu, one line per screen. Click `[Manage]` to open that screen's action page:

```
==================== CinePlay ====================
 main  [Playing 00:12/03:33]  3 items  [Manage]
 hall  [Empty]  —  [Manage]
  [+ New screen] [Refresh] [Help]
==================== CinePlay ====================
```

The screen page has every action as a button:
`[Playlist] [Next] [Add] [Volume] [Range] [Admin] [Trusted] [Delete]`.

Actions that need a value (add / volume / range / grant) ask you to **type the value in chat** after
clicking — no command syntax to remember, and normal chat keeps working afterwards.

### Command reference

| Command | Purpose |
| --- | --- |
| `/cineplay menu [screen]` | Clickable menu (top level / per screen) |
| `/cineplay screens` | List all screens |
| `/cineplay create` / `cancel` / `confirm <name>` | Creation flow |
| `/cineplay add [screen] <url> [seconds]` | Append to the playlist |
| `/cineplay playlist\|next\|prev [screen]` | Show list / skip |
| `/cineplay volume <screen> <0-100>` | Per-screen volume |
| `/cineplay range <screen> <enter> [exit]` | Per-screen view distance |
| `/cineplay access <screen> …` | Admin (can delete + grant) / `public true` = trust everyone |
| `/cineplay trust <screen> …` | Trusted (can control playback, cannot delete) |
| `/cineplay remove <screen>` | Delete a screen (asks for confirmation) |
| `/cineplay slot [name]` | Screen used by commands that omit the name |
| `/cineplay status` / `reload` / `help` | Status / reload config / help |

---

## Permissions — per screen

| Role | Control playback | Delete screen | Grant others |
| --- | --- | --- | --- |
| Owner (creator) | ✅ | ✅ | ✅ |
| Admin | ✅ | ✅ | ✅ |
| Trusted | ✅ | ❌ | ❌ |
| Public (`access <screen> public true`) | ✅ | ❌ | ❌ |
| OP | ✅ | ✅ | ✅ |

**Deleting is irreversible**, so "can play" and "can tear down" are kept apart: opening a screen to
everyone only grants usage — nobody gains the right to delete it that way.

---

## Playlist behaviour

- Added items queue up; when one finishes the next starts automatically
- **Duration** comes from `/cineplay add <screen> <url> 300` if you provide it; otherwise the plugin
  queries Bilibili, and as a last resort the client **reports the real duration once** at playback
  start — a value that naturally includes "non-premium accounts can only preview 6 minutes"
- **When the last item ends the queue is cleared** by default: `/cineplay playlist` becomes empty and
  `/cineplay status` reports finished. That makes "is it actually done?" observable instead of the
  server idling forever on item 1
- Set `playlistLoop: true` for looping playback (showcases, lobby reels)

---

## Configuration (`plugins/CinePlay/config.yml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `token` | empty | Must match the client's token; both empty = no check |
| `defaultSlot` | `main` | Screen used when a command omits the name |
| `premiumPreviewSeconds` | `360` | Preview-length fallback for premium content |
| `playlistLoop` | `false` | Loop the playlist |
| `menuRange` | `64` | "Nearby" radius for the menu, in blocks |
| `playCooldownMs` | `1500` | Cooldown between play requests |
| `requireChannel` | `false` | Only send when the client advertised the channel |
| `pasteTimeoutSeconds` | `60` | How long an input prompt stays armed |

---

## Installation

1. Drop the jar into the server's `plugins/` and restart.
2. Clients need **no configuration at all** (CineDuct accepts control messages from any server).
   Optional hardening: a `<gameDir>/cineduct-packet.json` restricting `token`, `allowedHosts`
   and `maxPerSecond`.
3. On the client, verify the channel with `/cineduct packet status`.

---

## FAQ

**The screen stays blank.**
Check the client log first: `虚拟屏 slot=…` means the geometry arrived, `进入范围：开始播放` means
playback started. Geometry present but no picture is usually the video source itself
(dead link or membership-only content).

**The size doesn't match what I selected.**
Geometry is computed at `confirm` and stored in `screens.yml`, but the plugin also keeps the **raw
corner points**: after changing geometry rules, restarting the server recomputes from those points,
so you never have to rebuild screens.

**It still reports playing after the video ended.**
By default the queue is cleared and marked finished. If you set `playlistLoop: true` it keeps looping.

**Audio but no video, or video but no audio.**
Make sure the client's CineDuct build matches the plugin (protocol `v=1`), then check the client log
for `服务端控制被拒` (message rejected by the gate).

---

## License

**MIT.** CinePlay and the CineDuct client mod (GPL-3.0-or-later) are two **independent programs**
that only exchange JSON over Minecraft plugin channels; neither contains the other's code.

Client mod: [github.com/hxwi1/CineDuct-API](https://github.com/hxwi1/CineDuct-API)
