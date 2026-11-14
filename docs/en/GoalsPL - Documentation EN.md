# GoalsPL - Documentation (EN)

GoalsPL adds community goals to Paper and Purpur servers. Staff create campaigns from menus and players complete
them together: kill mobs, break blocks or hand in materials. Milestones are announced and rewarded at the
percentages you choose, and finishing a campaign can start the next one automatically.

Español: [GoalsPL - Documentación ES](../es/GoalsPL%20-%20Documentaci%C3%B3n%20ES.md)

## Compatibility

| Server | Java | Status |
|---|---|---|
| Paper / Purpur 1.21.4 to 1.21.11 | 21 | Compiles and passes the tests against each version's API |
| Paper / Purpur 26.1.1, 26.1.2, 26.2 and 26.3 | 25 | Compiles and passes the tests against each version's API |
| Spigot, CraftBukkit, Folia | | Not supported |

One jar for every version. No NMS and no PacketEvents.

## Installation

1. Put `GoalsPL-<version>.jar` in `plugins/` (remove any older version first) and start the server. The SQLite
   driver is downloaded by the server the first time.
2. Optional: EventsPL, for `EVENT` rewards.
3. Join with permissions and run `/goals admin`.

## Getting started

Three example campaigns are included as drafts: `zombie_hunt` (mobs), `mining_week` (blocks; when it completes it
starts `harvest_donation`) and `harvest_donation` (hand in wheat and carrots). None of them counts until it is
started: open them in `/goals admin`, review them and click "Start", or use `/goals start <id>`.

To create a new one: `/goals admin`, "Create campaign", type the ID in chat and fill in the editor. Nothing is saved
until you click "Review and save", which shows a summary with errors (which block saving) and warnings.

## Concepts

### Campaigns and runs

A campaign is the definition (what counts, how much is needed, the rewards). Each time it is started a new run
begins with progress at zero. States:

| State | Meaning |
|---|---|
| Draft | Created or finished; does not count |
| Active | Running; progress counts |
| Paused | Does not count and the clock is stopped |
| Completed | Reached the goal |
| Cancelled | Ended by hand without completing |
| Expired | Time ran out without completing |

A campaign ID never changes: progress is tied to it, so editing a running campaign keeps its progress.

### Goal types

| Type | What counts |
|---|---|
| Defeat mobs | Listed mobs killed by a player |
| Break blocks | Listed blocks broken by a player |
| Deliver materials | Items players hand in from a menu |
| Admin progress | Only `/goals progress` and the API |

An empty target list means anything counts (except deliveries, which need at least one item). Each campaign can be
limited to some worlds.

### Time

Duration (`7d`, `12h`, `1d12h`) and deadline (`2026-12-31 23:59`, in the time zone from `config.yml`) are optional;
if both are set, whichever comes first applies. Pausing stops the clock, but the deadline does not move.

### Milestones and rewards

- **Community milestones:** percentages of the total (25, 50, 75, 100 or any you like). Each one has its rewards and
  who receives them:
  - **Community:** everyone who contributed anything and the players online at that moment.
  - **Participants:** only players who reached the campaign's minimum contribution.
- **Personal milestones:** rewards for each player when their contribution reaches an amount.
- **Leaderboard:** rewards by rank when the campaign completes.
- **Minimum and maximum per player:** the minimum decides who gets participant rewards; anything above the maximum
  does not count, so a few players cannot take over a campaign. These rules are shown in the menu before taking part.

Every milestone and reward is given only once per run, also across restarts.

### Reward types

Written as `TYPE: value`.

| Type | Value | Given to |
|---|---|---|
| `COMMAND` | Console command without slash: `eco give {player} 100` | Each player |
| `MESSAGE` | MiniMessage text | Each player |
| `ITEM` | `MATERIAL amount`: `DIAMOND 3` | Each player |
| `PERMISSION` | `permission duration`: `essentials.fly 1h` | Each player |
| `BROADCAST` | MiniMessage text for the whole server | Once |
| `GLOBAL_COMMAND` | Console command without slash | Once |
| `EVENT` | EventsPL event ID | Once |

Placeholders: `{player}`, `{goal_id}`, `{goal_name}`, `{progress}`, `{goal}`, `{percent}`, `{contribution}` and
`{rank}`.

- Anything that cannot be delivered (player offline, full inventory, a failing command) is kept as **pending** and
  retried on join, every `rewards.retry-minutes` minutes and with `/goals claim`.
- Temporary permissions do not need a permissions plugin: they are given while the player is online and come back on
  join until they expire. If the player already had it, the time is added.
- In commands, placeholder values are cleaned (letters, numbers, `_`, `.`, `-` and spaces only) and commands listed
  in `console-commands.blocked` are never run.

### Handing in materials

Items are never placed in the menu. The player picks a material and how many to hand in (left click 1, right click
16, shift for as many as possible) and confirms. On confirmation the items are counted again and only the accepted
amount is removed, never more than what is missing or than the player's maximum. Only plain items count (no name,
enchantments or damage) and only from the 36 inventory slots, never armor or the off hand.

If the campaign is cancelled or expires with "refunded", each player gets back what they handed in as a pending
reward.

### Stages

A campaign can name the next one (`next`). When it completes, the next campaign starts automatically and is
announced.

### Protection against fake progress

- Mobs only count when killed by a player, once each. Mobs from spawners do not count by default. With an empty list
  players and armor stands never count.
- Blocks placed by players do not count (they are marked in the chunk data). Crops only count when fully grown.
- Creative mode does not count by default, spectator never does.
- Other plugins can cancel a contribution with `CampaignContributionEvent`.

## Menus

| Menu | What it does |
|---|---|
| Main (`/goals`) | Active goals, my rewards and, with permission, administration |
| Active goals | Progress, time left and my contribution for each campaign (updates live) |
| Details | Milestones reached, my contribution and personal milestones, leaderboard, rules, hand-in button |
| Hand in | Requested materials, how many I have and how many I can hand in, with confirmation |
| My rewards | Pending rewards, with a button to claim them |
| Administration | All campaigns; create, global history, reload |
| Campaign panel | Start, pause, resume, complete, cancel (with confirmation), edit, clone, adjust progress, contributions, delivered or pending rewards, history, delete |
| Editor | Every field, targets, worlds, milestones, personal milestones, leaderboard, rewards (typed or from a preset), announcements, messages and bossbar |

Icons cannot be taken or moved, and every button checks its permission when clicked. Closing the editor without
saving discards the changes.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/goals` | `goalspl.use` | Main menu (help in the console) |
| `/goals help` | | Help |
| `/goals goals` | `goalspl.use` | Active goals (a list in the console) |
| `/goals info <id>` | `goalspl.use` | Campaign details |
| `/goals top <id>` | `goalspl.use` | Leaderboard |
| `/goals contribute <id>` | `goalspl.contribute` | Hand in materials |
| `/goals claim` | `goalspl.use` | Collect pending rewards |
| `/goals admin` | `goalspl.view` | Admin panel |
| `/goals start <id>` | `goalspl.manage` | Start; if it already ended, a new run from zero |
| `/goals pause <id>`, `/goals resume <id>` | `goalspl.manage` | Pause and resume |
| `/goals complete <id>` | `goalspl.manage` | Complete now: missing milestones, leaderboard and next stage |
| `/goals cancel <id>` | `goalspl.cancel` | Cancel |
| `/goals progress <id> <+n/-n/=n> [player]` | `goalspl.progress` | Add, subtract or set progress; with a player, also their contribution |
| `/goals history [id] [page]` | `goalspl.view` | History |
| `/goals reload` | `goalspl.reload` | Reload the files |

The main command is `/goalspl`; `/goals` is its alias. Everything works from the console except `contribute`,
`claim` and `admin`.

### Permissions

| Permission | Default | What for |
|---|---|---|
| `goalspl.use` | Everyone | Menu, goals, details, leaderboard and claiming |
| `goalspl.contribute` | Everyone | Hand in materials (mobs and blocks count without it) |
| `goalspl.view` | OP | Admin panel, read only |
| `goalspl.manage` | OP | Create, edit, clone, delete, start, pause, resume and complete |
| `goalspl.progress` | OP | Adjust progress |
| `goalspl.cancel` | OP | Cancel |
| `goalspl.reload` | OP | Reload |
| `goalspl.commands` | OP | Add or change console command rewards |
| `goalspl.admin` | OP | All of the above |

## Configuration

### `config.yml`

| Option | Default | What it does |
|---|---|---|
| `language` | `es` | Language used to create `messages.yml` (`es` or `en`) |
| `timezone` | system | Time zone for deadlines (`Europe/Madrid`) |
| `storage.file` | `data/goalspl.db` | SQLite file (changes on restart) |
| `storage.save-interval-seconds` | `2` | How often mob and block progress is saved; hand-ins are saved at once |
| `history.retention-days` | `90` | Days to keep history, hand-ins and delivered rewards |
| `counting.spawner-mobs` | `false` | Whether spawner mobs count |
| `counting.creative` | `false` | Whether creative mode counts |
| `counting.ignore-placed-blocks` | `true` | Do not count blocks placed by players |
| `counting.only-grown-crops` | `true` | Crops only count when fully grown |
| `rewards.deliver-on-join` | `true` | Deliver pending rewards on join |
| `rewards.retry-minutes` | `5` | Retry pending rewards of online players (0 = only on join and claim) |
| `leaderboard.size` | `10` | Ranks shown |
| `bossbar.enabled` | `true` | Global switch for bossbars |
| `console-commands.blocked` | `op`, `deop`, `stop`... | Commands a reward never runs |

Retention never deletes pending rewards or data from a campaign's current run.

To get English messages, set `language: en` before the first start, or delete `messages.yml` and restart.

### Other files

- `goals.yml`: the campaigns. It can be edited by hand and applied with `/goals reload`. A campaign with an invalid
  value is not loaded, but it is kept in the file and reported in the log. If the whole file cannot be read, nothing
  is saved from the game until it is fixed.
- `rewards.yml`: reward presets that can be added with one click from the editor.
- `messages.yml`: every text in MiniMessage. New keys from each version are added automatically.

### Data

`data/goalspl.db` stores the state and progress of each campaign, contributions per player, milestones reached,
delivered and pending rewards, hand-ins, temporary permissions and history. All disk access runs on its own thread,
never on the main one. A copy is saved to `data/backups/` before every schema change.

## API

```java
GoalsAPI api = Bukkit.getServicesManager().load(GoalsAPI.class);
api.campaigns();                 // every campaign (CampaignInfo)
api.running();                   // active or paused
api.contribution("zombie_hunt", uuid);
api.addProgress("zombie_hunt", uuid, "Steve", 5, "MyPlugin");   // returns the accepted amount
```

Bukkit events in `com.plgroup.goalspl.api.event`:

| Event | When |
|---|---|
| `CampaignStartedEvent` | Start, resume or start as the next stage (`previous()`) |
| `CampaignMilestoneEvent` | A milestone is reached (`percent()`) |
| `CampaignEndedEvent` | Completed, cancelled, expired or paused (`outcome()`) |
| `CampaignContributionEvent` | Before a contribution is added; cancellable |

Call the API from the main thread only.

## Known limitations

- Blocks moved by pistons lose their placed-by-player mark.
- Temporary permissions do not show up in the server's permissions plugin.
- Community rewards do not reach offline players who did not contribute.
- In the milestone editor the announcement is toggled with the Q key (middle click only works in creative).
