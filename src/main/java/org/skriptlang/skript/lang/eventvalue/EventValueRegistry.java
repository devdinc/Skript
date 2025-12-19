package org.skriptlang.skript.lang.eventvalue;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAPIException;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.registrations.Classes;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;
import org.skriptlang.skript.util.ClassUtils;
import org.skriptlang.skript.util.Registry;

import javax.swing.text.html.parser.Entity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and resolver for {@link EventValue} definitions.
 * <p>
 * Use this registry to register, unregister and resolve event values by identifier text or by
 * desired value type. Resolution prefers the closest matching event type and, optionally,
 * can fall back to default time state and/or allow value-type conversion.
 * <p>
 * Obtain an instance via {@code Skript.instance().registry(EventValueRegistry.class)}.
 */
public class EventValueRegistry implements Registry<EventValue<?, ?>> {

	/**
	 * When resolving a value for {@link EventValue#TIME_PAST}
	 */
	public static final int FALLBACK_TO_DEFAULT_TIME_STATE = 0b01;

	/**
	 * Allow converting the resolved value type using {@link Converters}
	 */
	public static final int ALLOW_CONVERSION = 0b10;

	/**
	 *  Default combination of resolution flags: fallback to default time and allow conversion.
	 */
	public static final int DEFAULT_RESOLVE_FLAGS = FALLBACK_TO_DEFAULT_TIME_STATE | ALLOW_CONVERSION;

	private final Skript skript;
	@SuppressWarnings("unchecked")
	private final List<EventValue<?, ?>>[] eventValues = new List[]{
		new ArrayList<>(),
		new ArrayList<>(),
		new ArrayList<>(),
	};

	private final transient Map<Input<?, ?>, Resolution<?, ?>> eventValuesCache = new ConcurrentHashMap<>();

	public EventValueRegistry(Skript skript) {
		this.skript = skript;
	}

	public Skript skript() {
		return skript;
	}

	/**
	 * Registers a new {@link EventValue}.
	 *
	 * @throws SkriptAPIException if another value with the same event class, time, and identifier patterns already exists
	 */
	public <E extends Event> void register(EventValue<E, ?> eventValue) {
		if (isRegistered(eventValue)) {
			throw new SkriptAPIException("Event '"
				+ eventValue.eventClass()
				+ "' already has a registered event value with the identifier patterns '"
				+ Arrays.toString(eventValue.identifierPatterns())
				+ "' (time: " + eventValue.time() + ")");
		}
		List<EventValue<?, ?>> eventValues = eventValues(eventValue.time());
		eventValues.add(eventValue);
		eventValuesCache.clear();
	}

	/**
	 * Unregisters the given event value.
	 *
	 * @return {@code true} if the value was removed
	 */
	public boolean unregister(EventValue<?, ?> eventValue) {
		boolean removed = eventValues(eventValue.time()).remove(eventValue);
		if (removed)
			eventValuesCache.clear();
		return removed;
	}

	/**
	 * Checks whether an equivalent event value is already registered.
	 */
	public boolean isRegistered(EventValue<?, ?> eventValue) {
		for (EventValue<?, ?> eventValue2 : eventValues(eventValue.time())) {
			if (!eventValue.eventClass().equals(eventValue2.eventClass())) continue;
			if (!eventValue.valueClass().equals(eventValue2.valueClass())) continue;
			if (!Arrays.equals(eventValue.identifierPatterns(), eventValue2.identifierPatterns())) continue;
			return true;
		}
		return false;
	}

	/**
	 * Checks whether a value for the exact event/value class and time is registered.
	 */
	public boolean isRegistered(Class<? extends Event> eventClass, Class<?> valueClass, int time) {
		for (EventValue<?, ?> eventValue2 : eventValues(time)) {
			if (!eventClass.equals(eventValue2.eventClass())) continue;
			if (!valueClass.equals(eventValue2.valueClass())) continue;
			return true;
		}
		return false;
	}

	/**
	 * Resolve an {@link EventValue} by identifier using {@link EventValue#TIME_NOW} and {@link #DEFAULT_RESOLVE_FLAGS}.
	 *
	 * @see #resolve(Class, String, int)
	 */
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier) {
		return resolve(eventClass, identifier, EventValue.TIME_NOW);
	}

	/**
	 * Resolve an {@link EventValue} by identifier for a specific time using {@link #DEFAULT_RESOLVE_FLAGS}.
	 *
	 * @see #resolve(Class, String, int, int)
	 */
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time) {
		return resolve(eventClass, identifier, time, DEFAULT_RESOLVE_FLAGS);
	}

	/**
	 * Resolve an {@link EventValue} by identifier with explicit time and flags.
	 *
	 * @param eventClass the event type to resolve for
	 * @param identifier user input that identifies the value
	 * @param time one of {@link EventValue#TIME_PAST}, {@link EventValue#TIME_NOW}, {@link EventValue#TIME_FUTURE}
	 * @param flags bitwise OR of {@link #FALLBACK_TO_DEFAULT_TIME_STATE} and {@link #ALLOW_CONVERSION}
	 * @return a {@link Resolution} describing candidates or empty/error state
	 */
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time, int flags) {
		if (time == EventValue.TIME_NOW)
			flags &= ~FALLBACK_TO_DEFAULT_TIME_STATE; // only 'past' and 'future' may use the fallback flag

		var input = Input.of(eventClass, identifier, time, flags);
		//noinspection unchecked
		var resolution = (Resolution<E, V>) eventValuesCache.get(input);
		if (resolution != null)
			return resolution;

		List<EventValue<E, V>> best = new ArrayList<>();
		int bestDist = Integer.MAX_VALUE;
		for (EventValue<?, ?> eventValue : eventValues(time)) {
			if (!ClassUtils.isRelatedTo(eventValue.eventClass(), eventClass) || !eventValue.matchesInput(identifier))
				continue;

			if (!eventValue.validate(eventClass))
				return Resolution.error();

			int distEvent = eventValue.eventClass().isAssignableFrom(eventClass)
				? ClassUtils.hierarchyDistance(eventValue.eventClass(), eventClass)
				: 1000 + ClassUtils.hierarchyDistance(eventClass, eventValue.eventClass()); // favor super events to sub events
			if (distEvent < bestDist) {
				best.clear();
				//noinspection unchecked
				best.add((EventValue<E, V>) eventValue.getConverted(eventClass, eventValue.valueClass()));
				bestDist = distEvent;
			} else if (distEvent == bestDist) {
				//noinspection unchecked
				best.add((EventValue<E, V>) eventValue.getConverted(eventClass, eventValue.valueClass()));
			}
		}
		if (!best.isEmpty()) {
			resolution = Resolution.of(best);
			eventValuesCache.put(input, resolution);
			return resolution;
		}

		if ((flags & FALLBACK_TO_DEFAULT_TIME_STATE) != 0)
			return resolve(eventClass, identifier, EventValue.TIME_NOW, flags);

		resolution = Resolution.empty();
		eventValuesCache.put(input, resolution);
		return resolution;
	}

	/**
	 * Resolves by desired value class using {@link EventValue#TIME_NOW} and {@link #DEFAULT_RESOLVE_FLAGS}.
	 */
	public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass) {
		return resolve(eventClass, valueClass, EventValue.TIME_NOW);
	}

	/**
	 * Resolves by desired value class for a specific time using {@link #DEFAULT_RESOLVE_FLAGS}.
	 */
	public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass, int time) {
		return resolve(eventClass, valueClass, time, DEFAULT_RESOLVE_FLAGS);
	}

	/**
	 * Resolves by desired value class with explicit time and flags.
	 *
	 * @param eventClass the event type to resolve for
	 * @param valueClass the desired value type
	 * @param time       one of {@link EventValue#TIME_PAST}, {@link EventValue#TIME_NOW}, {@link EventValue#TIME_FUTURE}
	 * @param flags      bitwise OR of {@link #FALLBACK_TO_DEFAULT_TIME_STATE} and {@link #ALLOW_CONVERSION}
	 */
	public <E extends Event, V> Resolution<E, ? extends V> resolve(
		Class<E> eventClass,
		Class<V> valueClass,
		int time,
		int flags
	) {
		if (time == EventValue.TIME_NOW)
			flags &= ~FALLBACK_TO_DEFAULT_TIME_STATE; // only 'past' and 'future' may use the fallback flag

		var input = Input.of(eventClass, valueClass, time, flags);
		//noinspection unchecked
//		var resolution = (Resolution<E, ? extends V>) eventValuesCache.get(input);
//		if (resolution != null)
//			return resolution;

		Resolution<E, ? extends V> resolution = resolveExact(eventClass, valueClass, time)
//			.anyOptional()
//			.map(eventValue -> Resolution.of(Collections.singletonList(eventValue)))
//			.orElse(Resolution.empty())
			;
		if (resolution.successful() || resolution.errored()) {
			eventValuesCache.put(input, resolution);
			return resolution;
		}

		resolution = resolveNearest(eventClass, valueClass, time);
		if (resolution.successful() || resolution.errored()) {
			eventValuesCache.put(input, resolution);
			return resolution;
		}

		if ((flags & ALLOW_CONVERSION) != 0) {
			resolution = resolveWithDowncastConversion(eventClass, valueClass, time);
			if (resolution.successful() || resolution.errored()) {
				eventValuesCache.put(input, resolution);
				return resolution;
			}

			resolution = resolveWithConversion(eventClass, valueClass, time);
			if (resolution.successful() || resolution.errored()) {
				eventValuesCache.put(input, resolution);
				return resolution;
			}
		}

		if ((flags & FALLBACK_TO_DEFAULT_TIME_STATE) != 0)
			return resolve(eventClass, valueClass, EventValue.TIME_NOW, flags);

		resolution = Resolution.empty();
		eventValuesCache.put(input, resolution);
		return resolution;
	}

	/**
	 * Resolves only exact value-class matches, choosing the nearest compatible event class.
	 */
	public <E extends Event, V> Resolution<E, V> resolveExact(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		List<EventValue<E, V>> best = new ArrayList<>();
		int bestDist = Integer.MAX_VALUE;
		for (EventValue<?, ?> eventValue : eventValues(time)) {
			if (!eventValue.eventClass().isAssignableFrom(eventClass) || !eventValue.valueClass().equals(valueClass))
				continue;

			if (!eventValue.validate(eventClass))
				return Resolution.error();

			int dist = ClassUtils.hierarchyDistance(eventValue.eventClass(), eventClass);
			if (dist < bestDist) {
				best.clear();
				//noinspection unchecked
				best.add((EventValue<E, V>) eventValue);
				bestDist = dist;
			} else if (dist == bestDist) {
				//noinspection unchecked
				best.add((EventValue<E, V>) eventValue);
			}
		}
		return Resolution.of(filterEventValues(valueClass, best));
	}

	/**
	 * Resolves to the nearest event and value class without conversion.
	 */
	private <E extends Event, V> Resolution<E, ? extends V> resolveNearest(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		List<EventValue<E, V>> best = new ArrayList<>();
		int bestDistEvent = Integer.MAX_VALUE, bestDistValue = Integer.MAX_VALUE;
		for (EventValue<?, ?> eventValue : eventValues(time)) {
			if (!ClassUtils.isRelatedTo(eventValue.eventClass(), eventClass)
				|| !valueClass.isAssignableFrom(eventValue.valueClass()))
				continue;

			if (!eventValue.validate(eventClass))
				return Resolution.error();

			int distEvent = eventValue.eventClass().isAssignableFrom(eventClass)
				? ClassUtils.hierarchyDistance(eventValue.eventClass(), eventClass)
				: 1000 + ClassUtils.hierarchyDistance(eventClass, eventValue.eventClass()); // favor super events to sub events
			int distValue = ClassUtils.hierarchyDistance(valueClass, eventValue.valueClass());
			if (distEvent < bestDistEvent || (distEvent == bestDistEvent && distValue < bestDistValue)) {
				best.clear();
				best.add(eventValue.getConverted(eventClass, valueClass));
				bestDistEvent = distEvent;
				bestDistValue = distValue;
			} else if (distEvent == bestDistEvent && distValue == bestDistValue) {
				best.add(eventValue.getConverted(eventClass, valueClass));
			}
		}
		return Resolution.of(filterEventValues(valueClass, best));
	}

	/**
	 * Resolves using downcast conversion when the desired value class is a supertype
	 * of the registered value class.
	 */
	private <E extends Event, V> Resolution<E, V> resolveWithDowncastConversion(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		List<EventValue<E, V>> best = new ArrayList<>();
		int bestDistEvent = Integer.MAX_VALUE, bestDistValue = Integer.MAX_VALUE;
		for (EventValue<?, ?> eventValue : eventValues(time)) {
			if (!ClassUtils.isRelatedTo(eventValue.eventClass(), eventClass)
				|| !eventValue.valueClass().isAssignableFrom(valueClass))
				continue;

			if (!eventValue.validate(eventClass))
				return Resolution.error();

			int distEvent = eventValue.eventClass().isAssignableFrom(eventClass)
				? ClassUtils.hierarchyDistance(eventValue.eventClass(), eventClass)
				: 1000 + ClassUtils.hierarchyDistance(eventClass, eventValue.eventClass()); // favor super events to sub events
			int distValue = ClassUtils.hierarchyDistance(valueClass, eventValue.valueClass());
			Converter<?, V> converter = source -> valueClass.isInstance(source) ? valueClass.cast(source) : null;
			if (distEvent < bestDistEvent || (distEvent == bestDistEvent && distValue < bestDistValue)) {
				best.clear();
				//noinspection unchecked,rawtypes
				best.add(eventValue.getConverted(eventClass, valueClass, (Converter) converter));
				bestDistEvent = distEvent;
				bestDistValue = distValue;
			} else if (distEvent == bestDistEvent && distValue == bestDistValue) {
				//noinspection unchecked,rawtypes
				best.add(eventValue.getConverted(eventClass, valueClass, (Converter) converter));
			}
		}
		return Resolution.of(filterEventValues(valueClass, best));
	}

	/**
	 * Resolves using {@link Converters} to convert value type when needed.
	 */
	private <E extends Event, V> Resolution<E, V> resolveWithConversion(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		List<EventValue<E, V>> best = new ArrayList<>();
		int bestDistEvent = Integer.MAX_VALUE;
		for (EventValue<?, ?> eventValue : eventValues(time)) {
			if (!ClassUtils.isRelatedTo(eventValue.eventClass(), eventClass))
				continue;

			if (!eventValue.validate(eventClass))
				return Resolution.error();

			int distEvent = eventValue.eventClass().isAssignableFrom(eventClass)
				? ClassUtils.hierarchyDistance(eventValue.eventClass(), eventClass)
				: 1000 + ClassUtils.hierarchyDistance(eventClass, eventValue.eventClass()); // favor super events to sub events
			if (distEvent < bestDistEvent && Converters.converterExists(eventValue.valueClass(), valueClass)) {
				best.clear();
				EventValue<E, V> converted = eventValue.getConverted(eventClass, valueClass);
				if (converted == null)
					continue;
				best.add(converted);
				bestDistEvent = distEvent;
			} else if (distEvent == bestDistEvent) {
				EventValue<E, V> converted = eventValue.getConverted(eventClass, valueClass);
				if (converted == null)
					continue;
				best.add(converted);
			}
		}
		return Resolution.of(best);
	}

	/**
	 * <p>
	 *  In this method we can strip converters that are able to be obtainable through their own 'event-classinfo'.
	 *  For example, {@link PlayerTradeEvent} has a {@link Player} value (player who traded)
	 *  	and an {@link AbstractVillager} value (villager traded from).
	 *  Beforehand, since there is no {@link Entity} value, it was grabbing both values as they both can be cast as an {@link Entity},
	 *  	resulting in a parse error of "multiple entities".
	 * 	Now, we filter out the values that can be obtained using their own classinfo, such as 'event-player'
	 * 		which leaves us only the {@link AbstractVillager} for 'event-entity'.
	 * </p>
	 */
	private <E extends Event, V> List<EventValue<E, V>> filterEventValues(
		Class<V> valueClass,
		List<EventValue<E, V>> eventValues
	) {
		if (eventValues.size() <= 1)
			return eventValues;
		List<EventValue<E, V>> filtered = new ArrayList<>();
		ClassInfo<V> requestedValueClassInfo = Classes.getExactClassInfo(valueClass);
		for (EventValue<E, V> eventValue : eventValues) {
			ClassInfo<V> eventValueClassInfo = Classes.getExactClassInfo(eventValue.valueClass());
			if (eventValueClassInfo != null && !eventValueClassInfo.equals(requestedValueClassInfo))
				continue;
			filtered.add(eventValue);
		}
		return filtered.isEmpty() ? eventValues : filtered;
	}

	private List<EventValue<?, ?>> eventValues(@Range(from = -1, to = 1) int time) {
		if (time < EventValue.TIME_PAST || time > EventValue.TIME_FUTURE)
			throw new IllegalArgumentException("Time must be -1, 0, or 1");
		return eventValues[time + 1];
	}

	/**
	 * Returns all registered event values at all time states.
	 */
	@Override
	public @Unmodifiable List<EventValue<?, ?>> elements() {
		return Arrays.stream(eventValues)
			.flatMap(List::stream)
			.toList();
	}

	/**
	 * Returns a snapshot of event values for the given time state.
	 *
	 * @param time one of {@link EventValue#TIME_PAST}, {@link EventValue#TIME_NOW}, {@link EventValue#TIME_FUTURE}
	 */
	public @Unmodifiable List<EventValue<?, ?>> elements(@Range(from = -1, to = 1) int time) {
		return List.copyOf(eventValues(time));
	}

	/**
	 * Result of a registry resolve operation. May contain multiple candidates or be empty.
	 * When {@link #errored()} is {@code true}, at least one candidate failed validation
	 * and no result should be used.
	 */
	public record Resolution<E extends Event, V>(List<EventValue<E, V>> all, boolean errored) {

		/**
		 *  Creates a successful resolution from candidates.
		 */
		public static <E extends Event, V> Resolution<E, V> of(List<EventValue<E, V>> eventValues) {
			return new Resolution<>(eventValues, false);
		}

		/**
		 *  Creates an empty, non-error resolution.
		 */
		public static <E extends Event, V> Resolution<E, V> empty() {
			return new Resolution<>(Collections.emptyList(), false);
		}

		/**
		 *  Creates an error resolution (e.g., a candidate failed validation).
		 */
		public static <E extends Event, V> Resolution<E, V> error() {
			return new Resolution<>(Collections.emptyList(), true);
		}

		/**
		 *  @return {@code true} if at least one candidate exists
		 */
		public boolean successful() {
			return !all.isEmpty();
		}

		/**
		 *  @return {@code true} if multiple candidates are available
		 */
		public boolean multiple() {
			return all.size() > 1;
		}

		/**
		 * @return the single candidate, or throws if none or many
		 * @throws IllegalStateException if the resolution is not unique
		 */
		public EventValue<E, V> unique() {
			if (all.size() != 1)
				throw new IllegalStateException("Resolution is not unique (size: " + all.size() + ")");
			return all.getFirst();
		}

		/**
		 *  @return the single candidate or {@code null} if not unique
		 */
		public EventValue<E, V> uniqueOrNull() {
			if (all.size() != 1)
				return null;
			return all.getFirst();
		}

		/**
		 *  @return the single candidate as an {@link Optional}, empty if not unique
		 */
		public Optional<EventValue<E, V>> uniqueOptional() {
			if (all.size() != 1)
				return Optional.empty();
			return Optional.of(all.getFirst());
		}

		/**
		 *  @return any candidate or {@code null} if none
		 */
		public EventValue<E, V> any() {
			if (all.isEmpty())
				return null;
			return all.getFirst();
		}

		/**
		 *  @return any candidate as an {@link Optional}, empty if none
		 */
		public Optional<EventValue<E, V>> anyOptional() {
			if (all.isEmpty())
				return Optional.empty();
			return Optional.of(all.getFirst());
		}

		/**
		 *  @return number of candidates contained
		 */
		public int size() {
			return all.size();
		}

	}

	private record Input<E extends Event, I>(
		Class<E> eventClass,
		I input,
		int time,
		int flags
	) {
		static <E extends Event> Input<E, String> of(Class<E> eventClass, String input, int time, int flags) {
			return new Input<>(eventClass, input, time, flags);
		}

		static <E extends Event> Input<E, Class<?>> of(Class<E> eventClass, Class<?> input, int time, int flags) {
			return new Input<>(eventClass, input, time, flags);
		}
	}

}
