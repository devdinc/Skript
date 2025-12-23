package org.skriptlang.skript.bukkit.particles.elements.expressions;

import ch.njol.skript.classes.Changer;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.bukkit.particles.particleeffects.ScalableEffect;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Particle Scale")
@Description("""
	Determines the scale of a particle.
	This only applies to explosion particles and sweep attack particles.
	Setting the particle scale will set particle count to 0, and scale will not take effect if count is greater than 0.
	
	Particles with counts greater than 0 do not have scale.
	
	More detailed information on particle behavior can be found at \
	<a href="https://docs.papermc.io/paper/dev/particles/#count-argument-behavior">Paper's particle documentation</a>.
	""")
@Example("set the scale of {_my-explosion-particle} to 2.3")
@Since("INSERT VERSION")
public class ExprParticleScale extends SimplePropertyExpression<ScalableEffect, Number> {

	public static void register(@NotNull SyntaxRegistry registry, @NotNull AddonModule.ModuleOrigin origin) {
		registry.register(SyntaxRegistry.EXPRESSION,
			infoBuilder(ExprParticleScale.class, Number.class, "scale [value]", "scalableparticles", false)
				.supplier(ExprParticleScale::new)
				.origin(origin)
				.build());
	}

	@Override
	public @Nullable Number convert(ScalableEffect from) {
		if (from.hasScale())
			return from.scale();
		return null;
	}

	@Override
	public Class<?> @Nullable [] acceptChange(Changer.ChangeMode mode) {
		return switch (mode) {
			case SET, ADD, REMOVE, RESET -> new Class[]{Number.class};
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, Changer.ChangeMode mode) {
		ScalableEffect[] scalableEffect = getExpr().getArray(event);
		if (scalableEffect.length == 0)
			return;
		double scaleDelta = delta == null ? 1 : ((Number) delta[0]).doubleValue();

		switch (mode) {
			case REMOVE:
				scaleDelta = -scaleDelta;
				// fallthrough
			case ADD:
				for (ScalableEffect effect : scalableEffect) {
					if (!effect.hasScale()) // don't set scale if it doesn't have one
						continue;
					effect.scale(effect.scale() + scaleDelta);
				}
				break;
			case SET:
				for (ScalableEffect effect : scalableEffect)
					effect.scale(scaleDelta);
				break;
			case RESET:
				for (ScalableEffect effect : scalableEffect) {
					if (!effect.hasScale()) // don't reset scale if it doesn't have one
						continue;
					effect.scale(scaleDelta);
				}
				break;
		}
	}

	@Override
	public Class<? extends Number> getReturnType() {
		return Number.class;
	}

	@Override
	protected String getPropertyName() {
		return "scale";
	}

}
