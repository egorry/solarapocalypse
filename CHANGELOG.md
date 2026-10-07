# Changelog

Follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
### Added
- Configurable phases (`config/solarapocalypse.cfg`): any number, lengths per phase or scaled, a safe phase 0.
- Apocalypse clock that follows the sun (works with day-length mods) or counts ticks; optional pause while the server
  is empty; `/time set` cannot rewind it.
- Cubic Chunks worlds: block conversions, erosion with a per-phase depth (or infinite), evaporation of water, lava and
  modded liquids; terrain loaded later catches up.
- Sun damage and fire for mobs in direct sunlight, background heat under thin cover, optional Fire Resistance immunity.
- `/solar` (alias of `/solarapocalypse`): status, set, add, phase, pause, resume, reload.
