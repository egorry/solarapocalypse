# Changelog

Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- Configurable phases (`config/solarapocalypse.cfg`): any number, lengths per phase or scaled, a safe phase 0.
- Apocalypse clock that follows the sun (works with day-length mods) or counts ticks; optional pause while the server
  is empty; `/time set` cannot rewind it.
- Cubic Chunks worlds: erosion with a per-phase depth (or infinite), conversions of the surface layer, evaporation of
  water, lava and modded liquids; terrain loaded later catches up.
- Depth counted from each column's own surface (computed from CubicWorldGen's generator, or recorded) or from a fixed Y.
- Rule modes: rules of earlier phases carry over (latest wins) or each phase stands alone.
- Sun damage and fire for mobs in direct sunlight, background heat wherever sky light reaches, optional Fire Resistance
  immunity.
- `/solar` (alias of `/solarapocalypse`): status, set, add, phase, pause, resume, reload.
- Block changes use a share of the free tick time, so a busy server slows the apocalypse instead of lagging.
- Phase length is a minimum: destruction runs first, then conversions, and the next phase waits for both.
- Solar fire: the sun sets a share of the surface alight after each phase's conversions (and on every layer of infinite
  erosion); it looks, sounds and burns like fire but never spreads. Flammable blocks get ordinary fire. The mod is now
  needed on clients too.
- Fire-immune mobs burn too, and direct sun burns at night as well, by default.
