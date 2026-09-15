# Stashlight

**Stop searching for items like a maniac and find your forgotten loot instantly.**

**Stashlight** is a lightweight, client-side Fabric mod that passively indexes every container you open, letting you instantly search for items across your world — even inside **Shulker Boxes**, **Bundles**, nested inventories, and **across dimensions**.

## Features

- **World-wide instant search**  
  Locate items in any indexed container

- **Nested container indexing**  
  Caches nested inventories, enabling searches inside shulker boxes and bundles

- **Enhanced Previews**  
  Full compatibility with **ShulkerBoxTooltip** for visual item previews directly in the search menu

- **Broad container support**  
  Works with Chests, Barrels, Shulkers, Hoppers, Droppers, Dispensers, and most modded block entities with inventories\*

- **Visual in-world highlighting**  
  Found what you need? Click the result to highlight the container in the world for easy retrieval

- **Cross-dimension tracking**  
  Tracks containers across Overworld, Nether, End, and modded dimensions\*

- **Client-side only**  
  Fully functional without server-side mods or plugins

<br/>

<sub>\* _Modded containers and dimensions are untested but expected to work_</sub>

## Usage

Press **NUMPAD 5** to open the search menu (keybind can be changed in Minecraft's controls settings).

### Litematica material gathering

With Litematica installed, open its material list and set any search, ignored-item,
or hide-available filters. Close that window, open Stashlight with **NUMPAD 5**, and
click **Highlight schematic materials**. Stashlight captures the last material
window's filtered entries, including rows outside the scroll viewport. If no
material window has been opened during this connection, it reads the active
material HUD's missing-material list instead (including entries beyond the HUD's
display limit).

The button replaces existing markers with every matching **cached container in
the current dimension**, ignoring Stashlight's search text, radius and
small-container filters. Open and close containers once to add them to the cache;
unknown containers are not scanned. Matching uses item types, including materials
inside shulker boxes and bundles.

Opening a container removes its world marker and highlights matching storage slots
in turquoise. A shulker box or bundle containing materials is highlighted as a
whole slot. Slot highlights follow the live inventory as items move, and the
selected material types stay active until **Clear all rendering**, another
material selection, a dimension change, or disconnect. Reopen the Litematica
material list and click the button again to capture updated requirements.

Litematica is optional. If it is missing, no list is active, or its API is
incompatible, the button displays a message without replacing existing markers.

![Stashlight Search Screen](https://cdn.modrinth.com/data/2ANiKmkM/images/8240485c0322a914f16022a7b936c62ecba33ba4_350.webp)

## Requirements

- [**Fabric API**](https://modrinth.com/mod/fabric-api)
- [**oωo (owo-lib)**](https://modrinth.com/mod/owo-lib)

## License

This project is licensed under the [**GNU General Public License v3.0**](https://github.com/Strange-Quark-007/Stashlight?tab=GPL-3.0-1-ov-file#readme)
