package org.skriptlang.skript.common.expressions;

import ch.njol.skript.Skript;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.util.Color;
import ch.njol.skript.util.ColorRGB;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

public class ExprColorFromHexCode extends SimplePropertyExpression<String, Color> {

	static {
		Skript.registerExpression(ExprColorFromHexCode.class, Color.class, ExpressionType.PROPERTY,
				"[the] colo[u]r[s] (from|of) hex[adecimal] code[s] %strings%");
	}


	@Override
	public @Nullable Color convert(String from) {
		if (from.startsWith("#")) // strip leading #
			from = from.substring(1);
		Color color = ColorRGB.fromHexString(from);
		if (color == null)
			error("Could not parse '" + from + "' as a hex code!");
		return color;
	}

	@Override
	public Class<? extends Color> getReturnType() {
		return Color.class;
	}

	@Override
	protected String getPropertyName() {
		assert false;
		return "ExprColorFromHexCode - UNSUSED";
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "the color of hex code " + getExpr().toString(event, debug);
	}

}
