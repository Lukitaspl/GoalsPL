![GoalsPL](https://raw.githubusercontent.com/Lukitas-bot/GoalsPL/main/modrinth/goalspl-banner.gif)

GoalsPL gives your community shared goals. Staff create campaigns from in-game menus and players complete them together: kill 500 zombies this week, mine 2000 ores, hand in 1000 wheat for the festival. Milestones are announced and rewarded along the way, and finishing one campaign can start the next.

`/goals admin` opens the admin panel. You create a campaign, review it (everything is validated before it goes live) and control it while it runs. Players use `/goals` to see active goals, their contribution, the leaderboard and their rewards.

## Goal types

| Type | What counts |
|---|---|
| Defeat mobs | Mobs from your list killed by a player |
| Break blocks | Blocks from your list broken by a player |
| Hand in materials | Items players hand in from a menu, with confirmation |
| Admin progress | Only staff commands or the API |

Each campaign can have a duration, a deadline or both, and can be limited to some worlds. A bossbar shows progress and time left.

## Milestones and rewards

- **Community milestones** at any percentage: for everyone, or only for players who reached a minimum contribution.
- **Personal milestones** when a player's own contribution reaches an amount.
- **Leaderboard rewards** for the top players when the campaign completes.
- **Minimum and maximum contribution per player**, shown to players before they take part, so a few players cannot take over a campaign.

| Reward | Example |
|---|---|
| Console command per player | `eco give {player} 100` |
| Message | `<green>Thanks {player}!` |
| Items | `DIAMOND 3` |
| Temporary permission | `essentials.fly 1h` (no permissions plugin needed) |
| Broadcast | Once for the whole server |
| One-time console command | `weather clear` |
| EventsPL event | Starts a timed event by its ID |

Rewards that cannot be delivered (offline player, full inventory, failing command) are kept and retried on join, every few minutes and with `/goals claim`. Ready-made rewards can be added with one click from `rewards.yml`.

## Built to be fair

- Every milestone and reward is given exactly once per run, also across restarts.
- Mobs count once and only when killed by a player; spawner mobs are ignored by default.
- Blocks placed by players and unripe crops never count. Creative mode does not count by default.
- Hand-ins never put items in the menu: the amount is confirmed, counted again and only plain items are taken, never armor or the off hand.
- If a hand-in campaign is cancelled or expires, items can be refunded automatically.
- Placeholder values are cleaned before they go into a command, and dangerous commands are blocked in `config.yml`.

## Commands

| Command | Description |
|---|---|
| `/goals` | Player menu |
| `/goals goals`, `/goals info <id>`, `/goals top <id>` | Active goals, details, leaderboard |
| `/goals contribute <id>`, `/goals claim` | Hand in materials, collect pending rewards |
| `/goals admin` | Admin panel |
| `/goals start <id>`, `pause`, `resume`, `complete`, `cancel` | Control a campaign |
| `/goals progress <id> <+n/-n/=n> [player]` | Adjust progress |
| `/goals history [id] [page]`, `/goals reload` | History, reload |

Permissions: `goalspl.use` and `goalspl.contribute` (everyone), `goalspl.view`, `goalspl.manage`, `goalspl.progress`, `goalspl.cancel`, `goalspl.reload`, `goalspl.commands` and `goalspl.admin`.

## For developers

`GoalsAPI` is available through Bukkit's ServicesManager to read campaigns and add progress. Events are fired when a campaign starts, reaches a milestone, ends, and before a contribution is added (cancellable).

## Requirements

- Paper or Purpur 1.21.4 to 26.3, one jar for all versions
- Java 21 (Java 25 for 26.x)
- Optional: EventsPL, for event rewards

Data is kept in SQLite with automatic backups before each schema change. Messages are included in English and Spanish.

## Links

- Documentation: [English](https://github.com/Lukitas-bot/GoalsPL/tree/main/docs/en), [Español](https://github.com/Lukitas-bot/GoalsPL/tree/main/docs/es)
- Source code and issues: [GitHub](https://github.com/Lukitas-bot/GoalsPL)

GoalsPL is made by PLGroup and distributed under the PLGroup License: free to use on any server, all other rights reserved. See [LICENSE](https://github.com/Lukitas-bot/GoalsPL/blob/main/LICENSE.txt).
