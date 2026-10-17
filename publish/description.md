# I.M.T Technology

**I.M.T** ("I Am Tunnelsensor") is a pair of AR glasses for Minecraft. Put them on and you get a
HUD with scanners, a drone arsenal, an orbital satellite, rayshields, holograms, remote redstone
and even voice control. Around the glasses grows a whole tech world: power grids, secured bases
with cameras and turrets, guard robots you build and program yourself, and hackable towers and
bunkers to raid.

## Getting started

- **The glasses cannot be crafted.** The only pair in your world lies in the vault at the top of
  a **Biebia's Industries Tower**, a 110-block skyscraper with a glowing logo and a blue beam
  from its crown that you can see from far away.
- The tower is guarded. A laser hall with rayshields, a detour with hidden switches, security
  turrets, four programmed security drones, and a trap in front of the vault chest stand between
  you and the glasses. Everything runs on power from a hidden power plant...
- ...or you **hack the tower's Security Core** with a Hacking Laptop. Its terminal is a small
  puzzle (port scan, logs, a password memory dump). Win it and the tower, its turrets and its
  drones are yours.
- Craft a **Computer Chip** (copper, gold, redstone, diamond): almost everything else needs one.
  Charge the glasses on a **Charging Station** powered by solar panels or heat generators.

## Features

**The glasses (press G for the round I.M.T menu)**
- HUD with energy, compass and target info; zoom, night vision, ore scan, threat detection.
- Mark targets and send a drone swarm, a single drone, an escort or a spy drone that writes a
  report. Ride a transport drone or fly with the autopilot.
- An orbital satellite with three fire modes (precision missile, bombardment, sweeping laser)
  that you upgrade by sending parts up with drones.
- Waypoints, pings and comms between glasses wearers. Illusions: fake walls, a fake army,
  an invisible field.

**Power and redstone**
- Solar panels, heat generators and drawn power cables (cut them with shears). A power cabinet
  with switches, and screens that show what your grid makes and uses.
- Wireless redstone relays with groups, an emitter, and the Arc Lamp with a light switch.

**Bases and security**
- A Security Core with its own desktop (Security OS): a camera wall, devices, keycards and code
  panels, files and traps, and protected build zones.
- Rayshields (laser gates in six colours), shield domes, rocket and laser turrets, security
  cameras, timed and remote C4 (defuse it with shears), cracks that stay until you repair them.
- Server racks with CPU, RAM, SSD, GPU and fans: program your base with redstone diagrams or a
  small Python-like language. Racks heat up, so give every rack a fan.
- Break-ins: hack keycard readers and code panels with the Bypass Kit, or pickpocket a keycard.

**Robots and drones**
- Build guard robots from heads, bodies, arms and legs in four styles, solder modules on a
  circuit board, and program what they see, where they go and when they shoot.
- Programmable security drones with their own dock, squads, and training sessions with lives
  instead of health.

**The tech suit**
- Tech Chestplate, Leggings and Boots: flight, a laser cannon in your hand and mini rockets.
  Upgrade them on the suit workbench and land on a landing pad.

**Optional extras (all off until you set them up)**
- **Voice**: I.M.T speaks (Piper) and understands push-to-talk commands (hold Z). Windows
  64-bit only.
- **AI brain**: understand free speech with a local model on your PC (free, offline) or with
  Claude (your own Anthropic API key, costs a little per sentence).
- **Music**: control Spotify ("I.M.T, play Thunderstruck by AC/DC") with your own free
  Spotify developer app. Starting music needs Spotify Premium.

## Controls

**G** opens the round I.M.T menu (scroll for the next tab). A few of the other keys: **V** zoom,
**N** night vision, **B** ore scan, **H** threat detection, **R** mark a target, **J** drone
swarm, **K** / **P** / **C** satellite, **M** navigation, **O** comms, **Z** push-to-talk.
Change them all in Options > Controls > I.M.T. While you wear working glasses, **P** and **L**
belong to I.M.T (vanilla's social interactions and advancements screens stay closed);
**C** and **X** are also vanilla's creative toolbar keys, so rebind one of them if you use those.

## Requirements

- Minecraft **1.21.1** with **NeoForge 21.1.251 or newer**.
- Install the mod on **both the client and the server**.
- Java 21 (the version Minecraft 1.21.1 uses).

## Privacy and downloads

- The mod sends nothing anywhere by itself.
- The voice and local AI parts are **not** in the jar. They are only downloaded when you ask for
  them in G > Systems, from the official GitHub and Hugging Face pages, and every file is checked
  against a fixed SHA-256 fingerprint. Voice control is about 1 GB, the local brain 1.3 to 5.7 GB.
- The microphone is only on **while you hold the push-to-talk key**.
- With the Claude brain, what you say is sent to Anthropic with your own API key, which stays on
  your PC. With Spotify, the mod only talks to Spotify with your own app and login.

## For server owners

- Every number in the mod (costs, damage, power, cooldowns, satellite levels and more, over 300
  of them) is in `config/imttech.toml` and is synced to the players.
- `[rules]` switches: `hackAllowed` (hacking a player lets you read their private messages
  while you can see them), `pickpocketAllowed`, `raidsEnabled` (raid drones come after glasses
  wearers). `/gamerule imttechRaidGriefing false` stops raid drones from breaking blocks.

## Credits and license

Made by **tunnelsensor**, with the help of Claude Code (an AI coding assistant). The textures,
sounds and buildings are made by the mod's own generator scripts.

License: **MIT**. The bundled libraries for the Claude brain keep their own licenses (MIT and
Apache 2.0, see `THIRD-PARTY-LICENSES_imttech.txt` in the jar). The optional voice and AI parts
are by rhasspy (Piper), ggml-org (llama.cpp) and Alibaba Qwen (Qwen models).

Not affiliated with Mojang, Microsoft, Anthropic or Spotify.
