package org.skriptlang.skript.bukkit.misc.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.docs.Origin;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Location with Yaw/Pitch")
@Description("Returns the given locations with the specified yaw and/or pitch.")
@Example("set {_location} to player's location with yaw 0 and pitch 0")
@Since("INSERT VERSION")
public class ExprWithYawPitch extends PropertyExpression<Location, Location> {

	public static void register(SyntaxRegistry registry, Origin origin) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression.builder(ExprWithYawPitch.class, Location.class)
			.supplier(ExprWithYawPitch::new)
			.origin(origin)
			.addPattern("%locations% with (:yaw|:pitch) %number%")
			.addPattern("%locations% with yaw %number% and pitch %number%")
			.build());
	}

	private Expression<Number> yaw, pitch;

	@Override
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		//noinspection unchecked
		setExpr((Expression<? extends Location>) expressions[0]);
		if (parseResult.hasTag("yaw")) {
			//noinspection unchecked
			yaw = (Expression<Number>) expressions[1];
		} else if (parseResult.hasTag("pitch")) {
			//noinspection unchecked
			pitch = (Expression<Number>) expressions[1];
		} else {
			//noinspection unchecked
			yaw = (Expression<Number>) expressions[1];
			//noinspection unchecked
			pitch = (Expression<Number>) expressions[2];
		}
		return true;
	}

	@Override
	protected Location[] get(Event event, Location[] source) {
		Number yaw = this.yaw != null ? this.yaw.getSingle(event) : null;
		Number pitch = this.pitch != null ? this.pitch.getSingle(event) : null;
		return get(source, location -> {
			float finalYaw = yaw != null ? yaw.floatValue() : location.getYaw();
			float finalPitch = pitch != null ? pitch.floatValue() : location.getPitch();
			return location.clone().setRotation(finalYaw, finalPitch);
		});
	}

	@Override
	public Class<? extends Location> getReturnType() {
		return Location.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		SyntaxStringBuilder builder = new SyntaxStringBuilder(event, debug);
		builder.append(getExpr(), "with");
		builder.appendIf(yaw != null, "yaw", yaw);
		builder.appendIf(yaw != null && pitch != null, "and");
		builder.appendIf(pitch != null, "pitch", pitch);
		return builder.toString();
	}

}
