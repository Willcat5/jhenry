# JHenry — Volume Mining Mode Plan

Status: approved spec; Phases 1-6 done (coordination wired), Phase 7-8 next.
Scope: expand JHenry beyond strip tunnels to carving out 3D volumes, selectable per
bot from the controller.

## 1. Overview

The mod currently maps and digs linear tunnels from trapdoor marks (`TunnelMark` +
`TunnelMapper` + `TunnelDigger`). Volume mode adds a second, independent mode in which
a marked region is carved into a `W x H x L` prism that is split into sub-volumes
("partitions") that up to four bots can clear in parallel.

Volume mode is a **per-bot toggle** in the controller. A bot is either in TUNNEL mode
(existing behavior) or VOLUME mode.

Movement reuses the existing controller stack (`MoveController` + `BlockBreaker` +
`LookController`). Volume clearing only needs forward walking and turning, so no
external pathfinding library is required.

## 2. Locked decisions

- Cross-section is defined by **two marks** (arbitrary blocks, no trapdoor requirement).
  The two marks are opposite corners of a **vertical rectangle**: `W` = horizontal
  span, `H` = vertical span.
- **Away direction** = the mapping bot's cardinal facing at the moment the map command
  is issued. The volume extrudes from the marks along `away`.
- **Length `L`** is a numeric input supplied when mapping.
- The region is split into **partitions**:
  - Height: `V = ceil(H/2)` layers, bottom-up, each 2 tall, top layer `H mod 2`
    (1 or 2) tall. H is capped at 6 by convention.
  - Width: two halves. For odd `W`, the half to the **right of away-facing**
    (clockwise from `away`) gets `ceil(W/2)`; the left half gets `floor(W/2)`.
  - Depth: never split, full `L`.
  - Partition count `= 2 * V`, max 6. Example `7w x 6h x 20d` -> 6 partitions
    (3 x `4x2x20` and 3 x `3x2x20`).
- **Mining patterns** (bot always stands on the volume floor, `y = volume bottom`):
  - `FLOOR` (bottom layer): mine the bot's own column horizontally.
  - `UPPER`: look up, mine the 2 blocks overhead, step forward one.
  - Both traverse a **snake**: full-depth pass, sidestep, return. No return to the
    start position after finishing.
- **Hazard policy**: when the next block to be mined would expose a hazard, **pause**
  the bot (do not abort). Operator resolves, then resumes.
- **Manual start**: the operator places the bot on a partition start and triggers the
  start manually, to avoid bot-vs-bot conflict. No automatic partition assignment.
- **Partition pickup**: the bot matches a partition by standing on its start position.
  Stacked partitions of the same width half share the same floor start, so the bot
  disambiguates by choosing the **lowest layer that still has unmined cells** (layers
  already fully air are skipped). Partially mined (paused) layers still match.
- On finish, the bot reports completion; the controller notifies the operator to
  relocate it to the next partition. The bot never walks itself back to the start.
- **No external movement library.** Reuse `MoveController`.

## 3. Geometry

Given marks `A`, `B` (opposite corners of the vertical rectangle), the map command's
`away` direction, and length `L`:

- Volume bottom `y` = `min(A.y, B.y)`.
- Width span `W` = |horizontal component of `A - B`| along the axis perpendicular to
  `away`.
- Height span `H` = `|A.y - B.y| + 1`.
- The near edge of the volume (where the marks live) is at the origin; the volume
  extends `L` blocks along `away`.
- Width axis `w` = horizontal perpendicular to `away`, resolved with
  `away.getClockWise()` for the "right" half.

Partition `p` is identified by `(layer, half)` with `layer in [0, V)` and
`half in {RIGHT, LEFT}`. For each partition:

- `min` / `max` inclusive bounds: width range of its half, y range of its layer,
  full depth along `away`.
- `start`: the near/away-origin corner of that half at `y = volume bottom`, facing
  `away`.
- `pattern`: `FLOOR` when `layer == 0`, else `UPPER`.

## 4. Client (mod) changes

### New data types (`willits.jhenry.mapping`)
- `MiningMode { TUNNEL, VOLUME }`.
- `VolumeMark(BlockPos a, BlockPos b, Direction away, int length, ResourceKey<Level> dimension)`.
- `Partition(BlockPos min, BlockPos max, BlockPos start, Direction away,
  Pattern pattern, int layer, MapConfig.Side half, int index)` with
  `enum Pattern { FLOOR, UPPER }`.
- `VolumePlan(VolumeMark mark, List<Partition> partitions, ...)`.
- `PartitionPlan(Partition partition, List<BlockPos> cells, StopReason stop, ...)`.

### New logic
- `VolumePartitioner` — pure geometry: `VolumeMark` -> `List<Partition>`.
- `VolumeMapper` — per-partition cells, reusing `BlockSafety.evaluate` +
  `GravelSafety` verdict logic (as `TunnelMapper.effectiveVerdict` does). Pauses on
  hazard.
- `VolumeDigger` — walks `PartitionPlan.cells()`; selects FLOOR vs UPPER target;
  snake ordering; reuses the ore-exposure, gravel-column and peek subtasks (extracted
  from `TunnelDigger` into shared helpers).

### Changed
- `MiningSettings`: add `miningMode` (`MiningMode` enum).
- `JHenryClient`: in VOLUME mode, marking accepts any block (drop the
  `TrapDoorBlock` requirement) and collects two corners; length numeric input.
- `PlanRenderer`: render volume partitions.
- `Persistence`: persist `miningMode`, volume marks and partitions.
- `ControlServer`: `mode` and `startPartition` command actions; expose
  `miningMode` in status. Field must **not** reuse `mode` (that is digger runtime
  state: `TunnelDigger.modeName()`).

## 5. Controller changes

- `BotStatus`: add `miningMode`, `volumeId`, `partitionIndex`, `partitionState`,
  partition-scoped progress.
- `BotCard`: per-bot TUNNEL/VOLUME toggle.
- `MainWindow`: per-bot mode map persisted to `.jhenry/modes.json`; push on online
  transition and on mismatch reconcile. Not stored on the `BotConfig` record
  (record is a map/list key; mutating it breaks identity).
- `VolumePanel`: partition list, status, assignment and start action.
- `BotClient`: `sendMode(...)`, `sendStartPartition(...)`.

## 6. Coordination

Mapping bot publishes volume geometry + partitions in `/status`. The controller holds
volume state; the operator assigns a partition; the controller sends the partition
spec; the bot generates cells locally and mines. The controller tracks claimed/done
and alerts on finish. (Confirm transport details at Phase 5.)

## 7. Phases

| # | Deliverable | Verified by |
|---|---|---|
| 1 | Data types + per-bot `miningMode` plumbing (no behavior change) | compiles, no behavior change |
| 2 | `VolumePartitioner` + `VolumeMapper` | render partitions in-world |
| 3 | `VolumeDigger` (reusing `MoveController`) | map + dig one partition |
| 4 | Marking/length input + GUI | place two marks, set L |
| 5 | `ControlServer` protocol (`mode`, `startPartition`) | curl |
| 6 | Controller toggle + `VolumePanel` | click toggle, assign partition |
| 7 | Persistence both sides | reload survives |
| 8 | Ore/gravel/peek/pause/resume parity | full run |

Phase-2 interim notes: corner marking was pulled forward (in VOLUME mode `M` marks
an arbitrary block; two corners are collected) so partitions are testable before
Phase 4. Extrusion length is `MapConfig.volumeLength`, controlled by a slider in both
the client `JHenryScreen` and the controller `SettingsWindow` (global, pushed via
`settings`), with an `Override Volume Length` checkbox mirroring `maxBlocksOverride`.
Partition entrances start at the right edge of each half. With exactly one corner
marked, the client shows a W/H action-bar readout and a preview box. The client panel
is normalized to an apparent GUI scale of 2 (uniform pose scale with inverse input
remap) so it looks consistent across video GUI scales.

Phase-3 notes: `VolumeDigger` follows the partition's anchors in snake order.
FLOOR passes are mined smooth: face the step direction, hold attack at a fixed
~30-degree downward pitch, and walk the full depth (movement is naturally paced by
the solid block ahead); an out-of-partition crosshair target releases attack but
walking continues. UPPER passes stand under each pair, look straight up, mine the
pair, then step forward. Hazards now only pause on gravel (block or block above is a
`FallingBlock`) and unbreakable; air/water/lava are the operator's responsibility.
Partition selection: position picks the width half; if pitch is above -60 (roughly
outward) select the floor (layer0), otherwise count consecutive air from the feet
upward and select layer = count/2 (clamped to the topmost). Not yet done (later
phases): gravel dig/cap handling, ore/peek parity, volume resume, cross-bot clearing.

Coordination (Phases 5-6): a bot publishes its first `VolumePlan` under `/status` as a
`volume` object (id, dimension, away, length, splitWidth, both corner positions, w/h/l,
and per-partition layer/half/pattern/away/min/max/start/done). The controller's
`VolumePanel` (sidebar "Volume") finds that volume and offers **Push Volume to All
Bots**, which sends a `setVolume` command (the two corners + away + length +
splitWidth + dimension) to every bot. Each bot rebuilds the plan locally
(`VolumeMapper.map`) and stores it. Partition assignment is then done in-game per bot
with the normal selection (stand at a partition start; pitch/air-count picks the
layer). No per-partition command is sent.

## 8. Open items

- Coordination transport details (Phase 5).
- In-world confirmation of the trapdoor/mark vertical relationship for the existing
  tunnel, for parity of the "volume floor" definition.
