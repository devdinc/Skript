package org.skriptlang.skript.bukkit.text;

import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.util.ConvertedExpression;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.LiteralUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.converter.ConverterInfo;

/**
 * Utilities for working with {@link Component}s.
 */
public final class TextComponentUtils {

	private static final ConverterInfo<Object, Component> OBJECT_COMPONENT_CONVERTER =
		new ConverterInfo<>(Object.class, Component.class, TextComponentUtils::plain, 0);

	private static final JoinConfiguration NEW_LINE_CONFIGURATION = JoinConfiguration.builder()
		.separator(Component.newline())
		.build();

	/**
	 * Creates a plain text component from an object.
	 * @param message The message to create a component from.
	 * @return An unprocessed component from the given message.
	 */
	public static Component plain(Object message) {
		return Component.text(message instanceof String ? (String) message : Classes.toString(message));
	}

	/**
	 * Joins components together with new line components.
	 * @param components The components to join.
	 * @return A component representing the provided components joined by new line components.
	 * @see Component#newline()
	 */
	public static Component joinByNewLine(ComponentLike... components) {
		return Component.join(NEW_LINE_CONFIGURATION, components);
	}

	/**
	 * Attempts to convert an expression into one that is guaranteed to return a component.
	 * @param expression The expression to convert.
	 * @return An expression that will wrap the output of {@code expression} in a {@link Component}.
	 * Will return null if {@code expression} is unable to be defended (see {@link LiteralUtils#defendExpression(Expression)}.
	 */
	public static @Nullable Expression<? extends Component> asComponentExpression(Expression<?> expression) {
		expression = LiteralUtils.defendExpression(expression);
		if (!LiteralUtils.canInitSafely(expression)) {
			return null;
		}

		//noinspection unchecked
		Expression<? extends Component> componentExpression = expression.getConvertedExpression(Component.class);
		if (componentExpression != null) {
			return componentExpression;
		}

		return new ConvertedExpression<>(expression, Component.class, OBJECT_COMPONENT_CONVERTER);
	}

	private TextComponentUtils() { }

}
