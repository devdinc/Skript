package ch.njol.skript.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Events;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.EventRestrictedSyntax;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import org.bukkit.event.Event;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.jetbrains.annotations.Nullable;

@Name("Chat Message")
@Description("The chat message in a chat event.")
@Example("""
	on chat:
		player has permission "admin"
		set the message to "<light red>%message%"
	""")
@Since("1.4.6")
@Events("chat")
public class ExprChatMessage extends SimpleExpression<String> implements EventRestrictedSyntax {

	static {
		Skript.registerExpression(ExprChatMessage.class, String.class, ExpressionType.SIMPLE, "[the] [chat( |-)]message");
	}

	private boolean isDelayed;

	@Override
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		this.isDelayed = isDelayed.isTrue();
		return true;
	}

	@Override
	protected String @Nullable [] get(Event event) {
		if (event instanceof AsyncPlayerChatEvent chatEvent) {
			return new String[]{chatEvent.getMessage()};
		}
		return new String[0];
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		if (isDelayed) {
			Skript.error("'" + toString(null, false) + "' can't be changed after the event has already passed!");
			return null;
		}
		return switch (mode) {
			case SET -> CollectionUtils.array(String.class);
			case DELETE -> {
				Skript.error("'" + toString(null, false) + "' can't be deleted." +
					" However, the event can be cancelled, which would prevent the message from being sent.");
				yield null;
			}
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		assert delta != null;
		if (event instanceof AsyncPlayerChatEvent chatEvent) {
			chatEvent.setMessage((String) delta[0]);
		}
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "the chat message";
	}

	@Override
	public Class<? extends Event>[] supportedEvents() {
		//noinspection unchecked
		return new Class[]{AsyncPlayerChatEvent.class};
	}

}
