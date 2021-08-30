# Client Script Mod

## Usage

#### run([Client Command]):
Run client side command, can only run commands registered to ClientCommandManager.DISPATCHER

#### player('attack'):
Attack entity or block player is looking at.

#### player('attack', 'block'):
Attack block if player is looking at a block.

#### player('attack', 'entity'):
Attack entity if player is looking at an entity.

#### player('attack', 'block', '[x] [y] [z]'):
Attack block at coordinate (x, y, z).
ex. player('attack', 'block', '70 65 89')
[#] Not that although you can assign wherever blocks, you can only attack blocks near than 6 blocks.
You can't attack blocks above world height neither.
See net.minecraft.server.network/ServerPlayerInteractionManager.class/processBlockBreakingAction() for more information.

#### player('attack', 'entity', '[Entity Selector]'):
Attack selected entity. Entity selector only support type, sort, limit, name, distance, x, y, z, dx, dy, dz.
ex. player('attack', 'entity', '@e[type=witch]')
[#] Note that although you can use entity selector to select whichever entities, you can only attack attackable entites near than 6 blocks.
Some entites, like items, experience orb, all kinds of arrows, and yourself, are not attackable.
Attacking unattackable entities will cause server disconnection.
ex. Run the command below in chat will cause disconnection.
player('attack', 'entity', '@s')
See net.minecraft.server.network/ServerPlayNetworkHandler.class/onPlayerInteractEntity() for more information.

#### player('drop'):
Drop all selected items.

#### player('drop', 'all'):
Drop all selected items.

#### player('drop', '[Number]'):
Drop selected [Number] items. [Number] should be a positive integer.
If [Number] is more than 64, it will only drop items 64 times.
ex. player('drop', '36')

#### player('swapHands'):
Swap the mainhand item with offhand item.

#### player('hotbar', '[Number]'):
Change hotbar to hotbar [Number]. [Number] should be 0 to 8.
ex. player('hotbar', '2')

## License

This mod is under the CC0 license.
