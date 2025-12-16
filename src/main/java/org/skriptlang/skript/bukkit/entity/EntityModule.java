package org.skriptlang.skript.bukkit.entity;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.entity.player.PlayerModule;
import org.skriptlang.skript.bukkit.entity.misc.*;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

public class EntityModule implements AddonModule {

	private List<AddonModule> subModules = new ArrayList<>(List.of(
		new PlayerModule()
	));

	@Override
	public void init(SkriptAddon addon) {
		subModules.removeIf(module -> !module.canLoad(addon));
		for (AddonModule module : subModules) {
			module.init(addon);
		}
	}

	@Override
	public void load(SkriptAddon addon) {
		for (AddonModule module : subModules) {
			module.load(addon);
		}
		subModules = null;

		// misc
		SyntaxRegistry syntaxRegistry = addon.syntaxRegistry();
		ExprDeathMessage.register(syntaxRegistry);
	}

}
