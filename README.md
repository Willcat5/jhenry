# JHenry

## Info

A client-side mod and controller packet designed to semi-automate strip mining in minecraft civilization events. Includes multi-client support,
quick view switching between bots, automatic mining for simple veins, and automatic tunnel generation to prevent bots from ever mining into
an open cave, lava, water, or gravel that would expose said dangers.

The intention of this project is to allow one player to enter an event on multiple accounts, and be able to significantly increase the productivity
of the civ's ability to mine. This is moreso a test for my larger library, "Caribou"

This mod is designed to be nearly undetectable, I went through numerous different baritone detection algortihims in decompiled anticheats. Namely
grim anticheat and vulcan. The bot includes natural randomization of pitch and yaw when turning, interpolation, and sensitivity scaling to make
things look seamless to the anticheat, and decently seamless to any admin that may be watching. Jhenry just looks like a brain-dead player mining
for their civ.

Jhenry does not automate some things, as large ore veins, pathfinding between tunnels, and handling of inventory storage are not intended for the
scope, as they are difficult to make look "human". These are intended to be included in the caribou API when it is finished.

This mod is designed for 1.21.11, due to its experimental nature, you'll have to build it yourself.

## Dependencies
Fabric loader, owo-lib, and fabric API
