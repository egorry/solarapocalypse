## The mod is remarkably configurable in config.

---

## Features and configurations:

"Phase 0" safe phase duration before apocalypse start.

Number of phases total.

How long each phase lasts, configurable per phase. Optionally set a scaling option: quadratic, linear, exponential e.t.c.

What phase onwards evaporates water and optionally (but by default) lava. We will set this up in such a way that liquids don't lag, but it *can* be configured otherwise - including allowing blocks to turn into liquids and vice versa. 

What blocks each phase turns into other blocks. E.g. minecraft:sand -> minecraft:glass. Also a way to do block groups (If Forge has those), and a special rule for converting unknown (modded) blocks to something). Also a way to do sub-IDs like somemod:someblock:4

What blocks each phase destroys. All blocks is a valid option too.

How far the phase can break or change, by block/layer. Infinite is a valid option too.

Percentage of exposed blocks ignited. This is further configured like the below to happen randomly but deterministically or instantly. This also refreshes each time a block layer is broken.

Whether the speed of phase effects (minus ignition) happens in the full duration of current phase -> next phase, or at a fixed rate determined by user, or instantly. If the amount of layers destroyed * user defined speed of destruction exceeds the phase -> phase time, it will be added to the phase -> phase time i.e. until the destruction effects are completely done. Block changes happen to random blocks but in a deterministic speed, or instantly on phase start.

What phase onwards hurts (and sets them on fire) all mobs in direct sunlight & that damage per second/tick per phase.

What phase onwards hurts (and sets them on fire) all mobs regardless of direct sunlight (but above ground) & that damage per second/tick per phase.

Everything happens retroactively on newly loaded chunks and old chunks when reloaded. Ideally without (much) lag.

Optional integration with Simple Difficulty.

Optional (default to off) flag for whether fire resistance grants immunity to apocalypse effects. Another setting which sets what fire resistance grants immunity to - direct exposure or background/undercover exposure

* By "instant" I mean spread out over ticks in order to not stall anything, but as fast as possible. If we need a threshold like only 75% as fast as possible to give some overhead, then we can do that as well/instead.
* If placing fire is laggy, we can create our own animated fire to place which is just a block. We can optionally (configurably) scan and ignite a portion of flammables as well.
* Default config should have leaves burn away before logs, so logs being removed leaving leaves shouldn't happen, but since it is configurable it technically can happen. In that case, don't rely on fast decay mods, we want to ideally cull leaves as well in some way to prevent lag, optionally (configurable).
* Falling blocks (sand, gravel) and flowing liquids (water, lava) should be configurable whether their block change or block removal triggers their physics.
* Modded liquids should be removed the same way, and never converted to or from something unless configured that way.
* Infinite destruction depth means all later phases (if any) share that setting. You cannot have phase 8 infinitely destroy layers and phase 9 only destroy 20 layers; phase 9 inherits infinite layer destruction off phase 8.

---

## Concessions:

If a dynamic number of phases isn't possible then we'll hardcode more than enough. What that number is depends on what I think the base phase effects should be, plus an extra margin for other user configurability.

---

## What won't be added:

An immunity period.

Blocks or items that protect from the sun or resist its effects.