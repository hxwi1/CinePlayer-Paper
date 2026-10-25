# CinePlay

**中文** | [English](#english)

服务端建**虚拟电影屏**的 Paper 插件：用指令在墙上框一块矩形，把 B 站视频/直播投在上面，
走近自动开播、走远自动停。

- 屏幕是**世界里的几何**，不是实体方块——不占地方、不进存档实体列表，只存坐标
- 播放由客户端模组负责（**CineDuct**），插件只下发"放什么、放哪儿、从哪儿开始"
- 完全解耦：只依赖 Paper API + 插件消息通道，**不需要模组的 jar**
- 没装模组的玩家不受影响（他们只是看不到屏幕）

协议见 [`PROTOCOL.md`](PROTOCOL.md)。Modrinth 项目描述见 [`MODRINTH-zh.md`](MODRINTH-zh.md) / [`MODRINTH-en.md`](MODRINTH-en.md)。

## 要求

| 项 | 值 |
| --- | --- |
| 服务端 | Paper 1.21.1（Bukkit / Spigot 理论可用，未专门测试） |
| 客户端 | 装了 **CineDuct** 模组的 NeoForge 1.21.1 客户端（仅 Windows x64） |
| 权限 | `cineplay.use` / `cineplay.admin`（默认 OP） |

## 安装

1. jar 放进 `plugins/`，重启服务端。
2. **客户端不需要任何配置** —— CineDuct 默认接受所有服务器的控制报文。
   想加保险的话（可选）：

```json
{
  "token": "",
  "disabledOps": [],
  "allowedHosts": ["bilibili.com", "bilibili.tv", "b23.tv", "bilivideo.com", "hdslb.com"],
  "maxPerSecond": 20
}
```

3. 客户端里 `/cineduct packet status` 核对通道是否通。

## 建一块屏

```
/cineplay create              进入选区（5 分钟不操作自动取消）
手持木锄右键两次               点两个对角（第三次点击＝重选第一个角）
/cineplay confirm <名字>       确认并命名（字母/数字/下划线/点/横线，1~32 字符）
/cineplay add <名字> <链接>    加片子；列表原本为空就立刻开播
```

几何规则：

- 平面由**你点到的方块面**决定（不是你的朝向），所以站哪儿点结果都一样
- 屏幕**竖直摆放**，两个点在该平面内取包围盒，尺寸 = 你圈的格数（点 2 格就是 2 格宽）
- 屏幕从墙面**浮出 0.05 格**，避免和方块表面 z-fighting（闪烁/穿模）
- 放 16:9 的视频到别的比例屏上会自动**信箱化**（缩视频，不拉伸）

## 指令

指令都围绕"屏幕"，没有"只给某个玩家放"这种东西。

| 指令 | 作用 |
| --- | --- |
| `/cineplay menu` | 一级菜单：我的屏 + 附近有权限的屏，每块一个 `[管理]` |
| `/cineplay menu <屏幕>` | 二级菜单：这块屏的全部操作（点按钮即可，不用记指令） |
| `/cineplay screens` | 列出全部屏幕 |
| `/cineplay create` / `cancel` / `confirm <名字>` | 建屏流程 |
| `/cineplay add [屏幕] <链接> [秒]` | 加进播放列表（秒数可省，插件会自己查） |
| `/cineplay playlist\|next\|prev [屏幕]` | 看列表 / 下一条 / 上一条 |
| `/cineplay volume <屏幕> <0-100>` | 这块屏的音量 |
| `/cineplay range <屏幕> <起播格> [停止格]` | 每屏的可视范围 |
| `/cineplay access <屏幕> add\|remove\|public\|list` | **管理员**（能删屏、能授权）/ 信任所有人 |
| `/cineplay trust <屏幕> add\|remove\|list` | **信任**（能操控播放，不能删） |
| `/cineplay remove <屏幕>` | 删屏（带二次确认） |
| `/cineplay slot [名字]` | 当前操作的屏幕（不带名字的指令作用在它上面） |
| `/cineplay status` / `reload` / `help` | 状态 / 重载配置 / 帮助 |

## 权限模型（每块屏各自一套）

| 身份 | 操控播放 | 删除屏幕 | 授权别人 |
| --- | --- | --- | --- |
| 主人（建屏的人） | ✅ | ✅ | ✅ |
| 管理员 | ✅ | ✅ | ✅ |
| 信任 | ✅ | ❌ | ❌ |
| 公开（`access <屏> public true` = 信任所有人） | ✅ | ❌ | ❌ |
| OP | ✅ | ✅ | ✅ |

删屏**不可撤销**，所以"能放片子"和"能拆屏"是分开的：公开只给使用权。

## 播放列表

- 每项可带时长（`/cineplay add main <链接> 300`）；不填的话插件会去 B 站查，
  再不行就等客户端起播时**上报一次**（客户端报的是它实际能播的时长，天然包含"非大会员试看 6 分钟"）
- 播完最后一项默认**清空队列**（`/cineplay playlist` 变空、`status` 显示播完）——
  这样"放完了"这件事看得见。想循环播放设 `playlistLoop: true`

## 配置（`plugins/CinePlay/config.yml`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `token` | `""` | 与客户端 `cineduct-packet.json` 的 `token` 一致才放行；两边都空 = 不校验 |
| `defaultSlot` | `main` | 默认操作的屏幕名 |
| `premiumPreviewSeconds` | `360` | 查不到客户端上报时，付费内容按试看时长兜底 |
| `playlistLoop` | `false` | 播完是否循环 |
| `menuRange` | `64` | 菜单里"附近"的范围（格） |
| `playCooldownMs` | `1500` | 重复开播冷却 |
| `requireChannel` | `false` | 是否要求客户端先通告通道才发 |
| `pasteTimeoutSeconds` | `60` | 点 `[加片]` 后粘贴链接的有效期 |

## 常见问题

**Q：屏幕不出现画面。**
A：先看客户端日志里有没有 `虚拟屏 slot=… 中心=…`（几何到没到），再看 `进入范围：开始播放`。
客户端 `/cineduct packet status` 能看到通道是否放行。

**Q：尺寸和我圈的不一样。**
A：几何在 `confirm` 时算好存进 `screens.yml`；改了几何规则后重启服务端会自动按**原始选点**重算，
不需要重建屏幕。

**Q：播完了还显示在播。**
A：默认播完会清空队列并标记"播完"。如果你设了 `playlistLoop: true`，它会一直循环。

## 许可

本插件以 **MIT** 发布（[`LICENSE`](LICENSE)）。它与 CineDuct 模组（GPL-3.0-or-later）是两个
**互相独立**的程序，只通过插件消息通道交换 JSON，详见 [`NOTICE.md`](NOTICE.md)。

---

# English

Server-side Paper plugin that builds **virtual cinema screens** in the world: frame a rectangle
with two clicks, play a Bilibili video/live stream on it, and playback starts when a player walks
close and stops when they leave.

- A screen is **geometry, not blocks** — nothing placed in the world, nothing in chunk data, just coordinates
- Actual playback is the client mod's job (**CineDuct**); the plugin only sends "what, where, from when"
- Fully decoupled: depends only on the Paper API + plugin messaging, **no mod jar needed**
- Players without the mod are unaffected (they simply don't see the screen)

Wire format: [`PROTOCOL.md`](PROTOCOL.md).

## Requirements

| Item | Value |
| --- | --- |
| Server | Paper 1.21.1 (Bukkit / Spigot likely work, untested) |
| Client | NeoForge 1.21.1 with the **CineDuct** mod (Windows x64 only) |
| Permissions | `cineplay.use` / `cineplay.admin` (default OP) |

## Installation

1. Drop the jar into `plugins/` and restart.
2. **No client configuration needed** — CineDuct accepts control messages from any server by default.
   Optional hardening (a `<gameDir>/cineduct-packet.json` file):

```json
{
  "token": "",
  "disabledOps": [],
  "allowedHosts": ["bilibili.com", "bilibili.tv", "b23.tv", "bilivideo.com", "hdslb.com"],
  "maxPerSecond": 20
}
```

3. In game, check the channel with `/cineduct packet status`.

## Creating a screen

```
/cineplay create              enter selection mode (auto-cancels after 5 min)
right-click twice with a wooden hoe   the two opposite corners
/cineplay confirm <name>      confirm and name it ([A-Za-z0-9_.-]{1,32})
/cineplay add <name> <url>    queue a video; starts immediately if the list was empty
```

Geometry rules:

- The plane comes from the **block face you clicked** (not your view direction), so where you stand doesn't matter
- Screens are **vertical**; the two points form a bounding box in that plane, size = the number of blocks you picked
- The screen floats **0.05 blocks off the wall** to avoid z-fighting with block faces
- Videos are **letterboxed** when their aspect ratio differs from the screen (no stretching)

## Commands

Everything is screen-centric; there is no "play for one specific player" mode.

| Command | Purpose |
| --- | --- |
| `/cineplay menu` | Top-level menu: your screens + nearby ones you may use, one `[Manage]` each |
| `/cineplay menu <screen>` | Screen menu: every action as a clickable button |
| `/cineplay screens` | List all screens |
| `/cineplay create` / `cancel` / `confirm <name>` | Screen creation flow |
| `/cineplay add [screen] <url> [seconds]` | Append to the playlist (duration is looked up if omitted) |
| `/cineplay playlist\|next\|prev [screen]` | Show list / next / previous |
| `/cineplay volume <screen> <0-100>` | Per-screen volume |
| `/cineplay range <screen> <enter> [exit]` | Per-screen view distance |
| `/cineplay access <screen> add\|remove\|public\|list` | **Admin** (can delete + grant) / open to everyone |
| `/cineplay trust <screen> add\|remove\|list` | **Trusted** (can control playback, cannot delete) |
| `/cineplay remove <screen>` | Delete a screen (asks for confirmation) |
| `/cineplay slot [name]` | Current screen for commands that omit the name |
| `/cineplay status` / `reload` / `help` | Status / reload config / help |

## Permissions (per screen)

| Role | Control playback | Delete screen | Grant others |
| --- | --- | --- | --- |
| Owner (creator) | ✅ | ✅ | ✅ |
| Admin | ✅ | ✅ | ✅ |
| Trusted | ✅ | ❌ | ❌ |
| Public (`access <screen> public true` = trust everyone) | ✅ | ❌ | ❌ |
| OP | ✅ | ✅ | ✅ |

Deleting is **irreversible**, so "can play" and "can tear down" are deliberately separate:
opening a screen to everyone grants usage only.

## Playlist behaviour

- Each item may carry a duration (`/cineplay add main <url> 300`); otherwise the plugin queries
  Bilibili, and as a last resort the client **reports the real duration once** at playback start —
  which naturally covers "non-premium accounts can only preview 6 minutes"
- When the last item finishes the queue is **cleared** by default (`/cineplay playlist` becomes empty
  and `status` says finished) so "done" is observable. Set `playlistLoop: true` to loop instead

## Configuration (`plugins/CinePlay/config.yml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `token` | `""` | Must match the client's `token`; both empty = no check |
| `defaultSlot` | `main` | Screen used when a command omits the name |
| `premiumPreviewSeconds` | `360` | Fallback preview length for premium content |
| `playlistLoop` | `false` | Loop the playlist |
| `menuRange` | `64` | "Nearby" radius for the menu, in blocks |
| `playCooldownMs` | `1500` | Cooldown between play requests |
| `requireChannel` | `false` | Only send when the client advertised the channel |
| `pasteTimeoutSeconds` | `60` | How long a `[Add]` paste prompt stays armed |

## FAQ

**The screen shows no picture.** Check the client log for `虚拟屏 slot=…` (geometry arrived) and
`进入范围：开始播放`; `/cineduct packet status` shows whether the gate allowed the message.

**The size doesn't match what I selected.** Geometry is computed at `confirm` time and stored in
`screens.yml`; after changing geometry rules, restarting the server recomputes from the stored
**raw corner points**, so you don't need to rebuild screens.

**It still says playing after the video ended.** By default the queue is cleared and marked finished.
If you set `playlistLoop: true` it keeps looping.

## License

MIT — see [`LICENSE`](LICENSE). CinePlay is a **separate program** from the CineDuct mod
(GPL-3.0-or-later); they only exchange JSON over plugin channels. See [`NOTICE.md`](NOTICE.md).
