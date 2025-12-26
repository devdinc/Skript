package org.skriptlang.skript.bukkit.misc.events;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.data.DefaultComparators;
import ch.njol.skript.entity.EntityData;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.registrations.EventValues;
import ch.njol.skript.util.slot.InventorySlot;
import ch.njol.skript.util.slot.Slot;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.lang.comparator.Relation;
import org.skriptlang.skript.registration.SyntaxRegistry;

public class EvtPlayerPickItem extends SkriptEvent {

	public static void register(SyntaxRegistry registry, Origin origin) {
		registry.register(BukkitSyntaxInfos.Event.KEY, BukkitSyntaxInfos.Event.builder(EvtPlayerPickItem.class, "Player Pick Item")
			.supplier(EvtPlayerPickItem::new)
			.origin(origin)
			.addEvent(PlayerPickBlockEvent.class)
			.addEvent(PlayerPickEntityEvent.class)
			.addPattern("[player] pick[ing] [an|any] item")
			.addPattern("[player] pick[ing] [a|any] block")
			.addPattern("[player] pick[ing] [an|any] entity")
			.addPattern("[player] pick[ing] %entitydata/itemtype/blockdata%")
			.addDescription("Called when a player picks an item, block or an entity" +
				" using the pick block key (default middle mouse button).")
			.addExample("""
				on player picking a diamond block:
					cancel event
					send "You cannot pick diamond blocks!" to the player
				""")
			.addSince("INSERT VERSION")
			.build());

		EventValues.registerEventValue(PlayerPickItemEvent.class, Slot.class, event -> {
			int source = event.getSourceSlot();
			if (source == -1)
				return null;
			return new InventorySlot(event.getPlayer().getInventory(), source);
		}, EventValues.TIME_PAST);
		EventValues.registerEventValue(PlayerPickItemEvent.class, Slot.class, event -> new InventorySlot(event.getPlayer().getInventory(), event.getTargetSlot()));
	}

	private @Nullable PickType pickType;
	private @Nullable Literal<?> type;

	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
		if (matchedPattern < 3) {
			pickType = PickType.values()[matchedPattern];
		} else {
			type = args[0];
		}
		return true;
	}

	@Override
	public boolean check(Event event) {
		if (pickType != null) {
			return switch (pickType) {
				case ANY -> true;
				case BLOCK -> event instanceof PlayerPickBlockEvent;
				case ENTITY -> event instanceof PlayerPickEntityEvent;
			};
		}

		Block pickedBlock;
		Entity pickedEntity;
		if (event instanceof PlayerPickBlockEvent pickBlockEvent) {
			pickedBlock = pickBlockEvent.getBlock();
			pickedEntity = null;
		} else if (event instanceof PlayerPickEntityEvent pickEntityEvent) {
			pickedEntity = pickEntityEvent.getEntity();
			pickedBlock = null;
		} else {
			assert false;
			return false;
		}
		assert type != null;
		return type.check(event, object -> switch (object) {
			case EntityData<?> entityData when pickedEntity != null -> entityData.isInstance(pickedEntity);
			case ItemType itemType when pickedEntity != null -> {
				Relation comparison = DefaultComparators.entityItemComparator.compare(EntityData.fromEntity(pickedEntity), itemType);
				yield Relation.EQUAL.isImpliedBy(comparison);
			}
			case ItemType itemType -> itemType.isOfType(pickedBlock);
			case BlockData blockData when pickedBlock != null -> pickedBlock.getBlockData().matches(blockData);
			default -> false;
		});
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		SyntaxStringBuilder builder = new SyntaxStringBuilder(event, debug);
		builder.append("player picking");
		if (pickType != null) {
			switch (pickType) {
				case ANY -> builder.append("an item");
				case BLOCK -> builder.append("a block");
				case ENTITY -> builder.append("an entity");
			}
		} else if (type != null) {
			builder.append(type);
		}
		return builder.toString();
	}

	private enum PickType {
		ANY,
		BLOCK,
		ENTITY,
	}

}
