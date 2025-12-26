package org.skriptlang.skript.test.tests.syntaxes.events;

import ch.njol.skript.sections.EffSecSpawn;
import ch.njol.skript.test.runner.SkriptJUnitTest;
import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class EvtPlayerPickItemTest extends SkriptJUnitTest {

	private Player player;
	private Pig pickedEntity;
	private Block pickedBlock;

	@Before
	public void setUp() {
		player = EasyMock.niceMock(Player.class);
		pickedEntity = spawnTestPig();
		pickedBlock = setBlock(Material.DIRT);
	}

	@Test
	@SuppressWarnings("UnstableApiUsage")
	public void test() {
		PlayerPickBlockEvent pickBlockEvent = new PlayerPickBlockEvent(player, pickedBlock, true, 0, 0);
		PlayerPickEntityEvent pickEntityEvent = new PlayerPickEntityEvent(player, pickedEntity, true, 0, 0);
		Bukkit.getPluginManager().callEvent(pickBlockEvent);

		Entity previous = EffSecSpawn.lastSpawned;
		EffSecSpawn.lastSpawned = pickedEntity;
		Bukkit.getPluginManager().callEvent(pickEntityEvent);
		EffSecSpawn.lastSpawned = previous;
	}

}
