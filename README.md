# SimpleBounty Plugin

**SimpleBounty** is a Paper 1.21.11 plugin that lets players put up item bounties on other players. Although it was custom built for [minecraftoffline.net](https://www.minecraftoffline.net), it can be used by any server.

## Features
- **Item Bounties**: Place any items (including enchanted, damaged or NBT-heavy) as the reward for eliminating a player. Durability, enchantments and all item data are preserved.
- **GUI Menus**: Browse active bounties, view rewards, see what is on you, and place new bounties through custom inventory GUIs.
- **Expiration System**: Bounties expire after a configurable duration and the items are returned to the placer.
- **Offline Support**: Place bounties on offline players, claim bounties when the placer is offline, and receive returned items whenever you next log in.
- **SQLite Storage**: Bounties and pending item returns are stored in a local SQLite database with WAL mode enabled.

## Installation
1. Download the latest `SimpleBounty.jar` from [Releases](https://github.com/Jelly-Pudding/simplebounty/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Commands

### Player commands
| Command | Description |
|---|---|
| `/bounty` | Opens the active bounty list GUI. |
| `/bounty list` | Same as above. |
| `/bounty place <player>` | Opens the placement GUI to put a bounty on a player. |
| `/bounty view <player>` | Shows the active bounties on a specific player. |
| `/bounty mine` | Shows the bounties you have placed. |
| `/bounty me` | Shows the bounties placed on you. |
| `/bounty cancel <id>` | Cancels one of your own bounties and returns the items. |
| `/bounty claimreturns` | Collects any items waiting from expired, cancelled, or overflowed bounties. |

Aliases: `/bounties`.

### Admin commands
| Command | Description |
|---|---|
| `/bountyadmin reload` | Reloads the plugin configuration. |
| `/bountyadmin list` | Lists all active bounties. |
| `/bountyadmin info <id>` | Shows detailed info about a bounty. |
| `/bountyadmin cancel <id>` | Cancels a bounty; items are returned to the placer. |
| `/bountyadmin cancelall <player>` | Cancels every bounty on a player. |
| `/bountyadmin clearplacer <player>` | Cancels every bounty a player has placed. |
| `/bountyadmin extend <id> <hours>` | Extends the expiration of a bounty by N hours. |

Aliases: `/bountya`, `/bountyadmin`.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `simplebounty.command.bounty` | `true` | Access to `/bounty` and all read-only subcommands. |
| `simplebounty.command.bounty.place` | `true` | Permission to place new bounties via `/bounty place`. |
| `simplebounty.command.admin` | `op` | Access to `/bountyadmin`. |

Revoke `simplebounty.command.bounty.place` from any group you do not want placing bounties.

## Configuration

```yaml
# SimpleBounty Configuration

# Default expiration time for a bounty in hours.
# After this time, the bounty is cancelled and items are returned to the placer.
default-expiration-hours: 8760

# Maximum number of active bounties a single player may have placed at once.
max-active-bounties-per-placer: 10

# Rate limit on how many bounties a single player may PLACE in a rolling time window.
# This prevents spam from repeatedly placing and cancelling to keep chat churning.
# Counts are kept in memory and reset on server restart.
# Set placement-rate-limit.enabled to false to disable entirely.
placement-rate-limit:
  enabled: true
  max-placements: 20
  window-minutes: 60

# Maximum number of item stacks that can be included in a single bounty.
# The placement GUI has 45 item slots so this cannot exceed 45.
max-items-per-bounty: 45

# If true, placing a bounty on yourself is allowed.
allow-self-bounty: false

# If true, the placer of a bounty can also claim it (by killing the target themselves).
# Set to false to prevent wash-trading - a player putting a bounty on a friend and
# having that friend let them be killed so the placer "claims" their own reward.
allow-placer-self-claim: true

# How often (in seconds) the expiration checker runs.
expiration-check-interval-seconds: 60

# If true, broadcast a chat message when a bounty is placed.
announce-placement: true

# If true, broadcast a chat message when a bounty is claimed.
announce-claim: true

# If true, broadcast a chat message when a bounty expires and items are returned.
announce-expiration: false

# Discord announcements via DiscordRelay (if installed - see https://github.com/Jelly-Pudding/minecraft-discord-relay).
discord:
  enabled: true
  announce-placement: true
  announce-claim: true
  announce-expiration: false

# When a player's inventory is full on claim or return, drop overflow items at their feet.
# If false, items are saved in the pending-returns table for later collection
# with /bounty claimreturns.
drop-on-full-inventory: false
```

| Key | Default | Description |
|---|---|---|
| `default-expiration-hours` | `8760` | Hours before a bounty automatically expires and items are returned. Defaults to one year (365 days). |
| `max-active-bounties-per-placer` | `10` | Maximum bounties a single placer may have active at once. |
| `placement-rate-limit.enabled` | `true` | Enable the rolling-window placement rate limit. |
| `placement-rate-limit.max-placements` | `20` | Max placements allowed inside the window. |
| `placement-rate-limit.window-minutes` | `60` | Length of the rolling window in minutes. |
| `max-items-per-bounty` | `45` | Maximum item stacks per bounty (cannot exceed 45, the GUI size). |
| `allow-self-bounty` | `false` | If true, players can bounty themselves. |
| `allow-placer-self-claim` | `true` | If false, the placer cannot claim their own bounty (mitigates wash-trading). |
| `expiration-check-interval-seconds` | `60` | How often the expiration sweeper runs. |
| `announce-placement` | `true` | Broadcasts new bounties to chat. |
| `announce-claim` | `true` | Broadcasts successful claims to chat. |
| `announce-expiration` | `false` | Broadcasts expirations to chat. |
| `discord.enabled` | `true` | Master switch for Discord announcements (requires DiscordRelay). |
| `discord.announce-*` | `true`/`false` | Per-event Discord announcement toggles. |
| `drop-on-full-inventory` | `false` | Drop overflow items at the player's feet instead of saving to pending returns. |

## How it works

### Placing a bounty
Run `/bounty place <player>` to open the placement GUI. Drop items into the 45 reward slots, then click the confirm button. The items are removed from your inventory and stored in SQLite alongside the bounty.

### Claiming a bounty
Kill the target. Any active bounties on them are resolved and the reward items are placed in your inventory (or saved to pending returns if you cannot carry them all).

### Expirations and returns
A background task runs once per `expiration-check-interval-seconds`. Expired bounties are cancelled and their items are queued back to the placer. If the placer is online, they are auto-delivered. Otherwise the items sit in the pending returns queue until they log in and run `/bounty claimreturns`.

## Support Me
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
