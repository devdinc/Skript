package org.skriptlang.skript.bukkit.entity.misc;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Events;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.EventRestrictedSyntax;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Death Message")
@Description("The message sent to all online players when a player dies.")
@Example("""
	on death of player:
		set the death message to "%player% died!"
	""")
@Since("2.0")
@Events("death")
public class ExprDeathMessage extends SimpleExpression<Component> implements EventRestrictedSyntax {

	public static void register(SyntaxRegistry syntaxRegistry) {
		syntaxRegistry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression.builder(ExprDeathMessage.class, Component.class)
			.supplier(ExprDeathMessage::new)
			.priority(SyntaxInfo.SIMPLE)
			.addPattern("[the] death( |-)message")
			.build());
	}

	private boolean isDelayed;

	@Override
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		this.isDelayed = isDelayed.isTrue();
		return true;
	}

	@Override
	protected Component @Nullable [] get(Event event) {
		if (event instanceof PlayerDeathEvent deathEvent) {
			return new Component[]{deathEvent.deathMessage()};
		}
		return new Component[0];
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		if (isDelayed) {
			Skript.error("'" + toString(null, false) + "' can't be changed after the event has already passed!");
			return null;
		}
		return switch (mode) {
			case SET, DELETE -> CollectionUtils.array(Component.class);
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		Component message = delta == null ? null : (Component) delta[0];
		if (event instanceof PlayerDeathEvent deathEvent) {
			deathEvent.deathMessage(message);
		}
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends Component> getReturnType() {
		return Component.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "the death message";
	}

	@Override
	public Class<? extends Event>[] supportedEvents() {
		//noinspection unchecked
		return new Class[]{EntityDeathEvent.class};
	}

}
