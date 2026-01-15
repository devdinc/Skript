package org.skriptlang.skript.bukkit.entity.player;

import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.entity.player.elements.*;
import org.skriptlang.skript.registration.SyntaxRegistry;

public class PlayerModule implements AddonModule {

	@Override
	public void load(SkriptAddon addon) {
		SyntaxRegistry syntaxRegistry = addon.syntaxRegistry();
		ExprJoinMessage.register(syntaxRegistry);
		ExprKickMessage.register(syntaxRegistry);
		ExprOnScreenKickMessage.register(syntaxRegistry);
		ExprPlayerListHeaderFooter.register(syntaxRegistry);
		ExprPlayerListName.register(syntaxRegistry);
		ExprQuitMessage.register(syntaxRegistry);
	}

	@Override
	public String name() {
		return "player";
	}

}
