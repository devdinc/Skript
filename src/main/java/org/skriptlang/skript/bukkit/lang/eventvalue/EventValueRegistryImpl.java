package org.skriptlang.skript.bukkit.lang.eventvalue;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAPIException;
import com.google.common.base.Preconditions;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.Unmodifiable;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;
import org.skriptlang.skript.util.ClassUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class EventValueRegistryImpl implements EventValueRegistry {

	private final Skript skript;

	@SuppressWarnings("unchecked")
	private final List<EventValue<?, ?>>[] eventValues = new List[]{
		new ArrayList<>(),
		new ArrayList<>(),
		new ArrayList<>(),
	};

	private final transient Map<Input<?, ?>, Resolution<?, ?>> eventValuesCache = new ConcurrentHashMap<>();

	public EventValueRegistryImpl(Skript skript) {
		this.skript = skript;
	}

	@Override
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

	@Override
	public boolean unregister(EventValue<?, ?> eventValue) {
		boolean removed = eventValues(eventValue.time()).remove(eventValue);
		if (removed)
			eventValuesCache.clear();
		return removed;
	}

	@Override
	public boolean isRegistered(EventValue<?, ?> eventValue) {
		for (EventValue<?, ?> eventValue2 : eventValues(eventValue.time())) {
			if (!eventValue.eventClass().equals(eventValue2.eventClass())) continue;
			if (!eventValue.valueClass().equals(eventValue2.valueClass())) continue;
			if (!Arrays.equals(eventValue.identifierPatterns(), eventValue2.identifierPatterns())) continue;
			return true;
		}
		return false;
	}

	@Override
	public boolean isRegistered(Class<? extends Event> eventClass, Class<?> valueClass, int time) {
		for (EventValue<?, ?> eventValue2 : eventValues(time)) {
			if (!eventClass.equals(eventValue2.eventClass())) continue;
			if (!valueClass.equals(eventValue2.valueClass())) continue;
			return true;
		}
		return false;
	}

	@Override
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier) {
		return resolve(eventClass, identifier, EventValue.TIME_NOW);
	}

	@Override
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time) {
		return resolve(eventClass, identifier, time, DEFAULT_RESOLVE_FLAGS);
	}

	@Override
	public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time, int flags) {
		Preconditions.checkNotNull(eventClass, "eventClass");
		Preconditions.checkNotNull(identifier, "identifier");

		if (time == EventValue.TIME_NOW)
			flags &= ~FALLBACK_TO_DEFAULT_TIME_STATE; // only 'past' and 'future' may use the fallback flag

		var input = Input.of(eventClass, identifier, time, flags);
		//noinspection unchecked
		var resolution = (Resolution<E, V>) eventValuesCache.get(input);
		if (resolution != null)
			return resolution;

		//noinspection unchecked
		resolution = Resolver.<E, V>builder(eventClass)
			.filter(ev -> ClassUtils.isRelatedTo(ev.eventClass(), eventClass) && ev.matchesInput(identifier))
			.comparator(Resolver.EVENT_DISTANCE_COMPARATOR)
			.mapper(ev -> (EventValue<E, V>) ev.getConverted(eventClass, ev.valueClass()))
			.build()
			.resolve(eventValues(time));

		if (resolution.successful()) {
			eventValuesCache.put(input, resolution);
			return resolution;
		}

		if ((flags & FALLBACK_TO_DEFAULT_TIME_STATE) != 0)
			return resolve(eventClass, identifier, EventValue.TIME_NOW, flags);

		resolution = Resolution.empty();
		eventValuesCache.put(input, resolution);
		return resolution;
	}

	@Override
	public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass) {
		return resolve(eventClass, valueClass, EventValue.TIME_NOW);
	}

	@Override
	public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass, int time) {
		return resolve(eventClass, valueClass, time, DEFAULT_RESOLVE_FLAGS);
	}

	@Override
	public <E extends Event, V> Resolution<E, ? extends V> resolve(
		Class<E> eventClass,
		Class<V> valueClass,
		int time,
		int flags
	) {
		Preconditions.checkNotNull(eventClass, "eventClass");
		Preconditions.checkNotNull(valueClass, "valueClass");

		if (time == EventValue.TIME_NOW)
			flags &= ~FALLBACK_TO_DEFAULT_TIME_STATE; // only 'past' and 'future' may use the fallback flag

		var input = Input.of(eventClass, valueClass, time, flags);
		//noinspection unchecked
		var resolution = (Resolution<E, ? extends V>) eventValuesCache.get(input);
		if (resolution != null)
			return resolution;

		resolution = resolveExact(eventClass, valueClass, time)
			.anyOptional()
			.map(eventValue -> Resolution.of(Collections.singletonList(eventValue)))
			.orElse(Resolution.empty());
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

	@Override
	public <E extends Event, V> Resolution<E, V> resolveExact(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		return Resolver.builder(eventClass, valueClass)
			.filter(ev -> ev.eventClass().isAssignableFrom(eventClass) && ev.valueClass().equals(valueClass))
			.comparator(Resolver.EVENT_DISTANCE_COMPARATOR)
			.filterMatches()
			.build().resolve(eventValues(time));
	}

	/**
	 * Resolves to the nearest event and value class without conversion.
	 */
	private <E extends Event, V> Resolution<E, ? extends V> resolveNearest(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		return Resolver.builder(eventClass, valueClass)
			.filter(ev -> ClassUtils.isRelatedTo(ev.eventClass(), eventClass) && valueClass.isAssignableFrom(ev.valueClass()))
			.comparator(Resolver.EVENT_VALUE_DISTANCE_COMPARATOR)
			.mapper(ev -> ev.getConverted(eventClass, valueClass))
			.filterMatches()
			.build().resolve(eventValues(time));
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
		Converter<?, V> converter = source -> valueClass.isInstance(source) ? valueClass.cast(source) : null;
		//noinspection unchecked,rawtypes
		return Resolver.builder(eventClass, valueClass)
			.filter(ev -> ClassUtils.isRelatedTo(ev.eventClass(), eventClass)
				&& ev.valueClass().isAssignableFrom(valueClass))
			.comparator(Resolver.EVENT_VALUE_DISTANCE_COMPARATOR)
			.mapper(ev -> ev.getConverted(eventClass, valueClass, (Converter) converter))
			.filterMatches()
			.build().resolve(eventValues(time));
	}

	/**
	 * Resolves using {@link Converters} to convert value type when needed.
	 */
	private <E extends Event, V> Resolution<E, V> resolveWithConversion(
		Class<E> eventClass,
		Class<V> valueClass,
		int time
	) {
		return Resolver.builder(eventClass, valueClass)
			.filter(ev -> ClassUtils.isRelatedTo(ev.eventClass(), eventClass))
			.comparator(Resolver.BI_EVENT_DISTANCE_COMPARATOR)
			.mapper(ev -> ev.getConverted(eventClass, valueClass))
			.build().resolve(eventValues(time));
	}

	private List<EventValue<?, ?>> eventValues(@Range(from = -1, to = 1) int time) {
		if (time < EventValue.TIME_PAST || time > EventValue.TIME_FUTURE)
			throw new IllegalArgumentException("Time must be -1, 0, or 1");
		return eventValues[time + 1];
	}

	@Override
	public @Unmodifiable List<EventValue<?, ?>> elements() {
		return Arrays.stream(eventValues)
			.flatMap(List::stream)
			.toList();
	}

	@Override
	public @Unmodifiable List<EventValue<?, ?>> elements(@Range(from = -1, to = 1) int time) {
		return List.copyOf(eventValues(time));
	}

	@Override
	public EventValueRegistry unmodifiableView() {
		return new UnmodifiableView();
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

	private class UnmodifiableView implements EventValueRegistry {

		@Override
		public <E extends Event> void register(EventValue<E, ?> eventValue) {
			throw new UnsupportedOperationException("Cannot register event values with an unmodifiable event value registry.");
		}

		@Override
		public boolean unregister(EventValue<?, ?> eventValue) {
			throw new UnsupportedOperationException("Cannot unregister event values from an unmodifiable event value registry.");
		}

		@Override
		public boolean isRegistered(EventValue<?, ?> eventValue) {
			return EventValueRegistryImpl.this.isRegistered(eventValue);
		}

		@Override
		public boolean isRegistered(Class<? extends Event> eventClass, Class<?> valueClass, int time) {
			return EventValueRegistryImpl.this.isRegistered(eventClass, valueClass, time);
		}

		@Override
		public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier) {
			return EventValueRegistryImpl.this.resolve(eventClass, identifier);
		}

		@Override
		public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time) {
			return EventValueRegistryImpl.this.resolve(eventClass, identifier, time);
		}

		@Override
		public <E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, int time, int flags) {
			return EventValueRegistryImpl.this.resolve(eventClass, identifier, time, flags);
		}

		@Override
		public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass) {
			return EventValueRegistryImpl.this.resolve(eventClass, valueClass);
		}

		@Override
		public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass, int time) {
			return EventValueRegistryImpl.this.resolve(eventClass, valueClass, time);
		}

		@Override
		public <E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass, int time, int flags) {
			return EventValueRegistryImpl.this.resolve(eventClass, valueClass, time, flags);
		}

		@Override
		public <E extends Event, V> Resolution<E, V> resolveExact(Class<E> eventClass, Class<V> valueClass, int time) {
			return EventValueRegistryImpl.this.resolveExact(eventClass, valueClass, time);
		}

		@Override
		public @Unmodifiable List<EventValue<?, ?>> elements() {
			return EventValueRegistryImpl.this.elements();
		}

		@Override
		public @Unmodifiable List<EventValue<?, ?>> elements(@Range(from = -1, to = 1) int time) {
			return EventValueRegistryImpl.this.elements(time);
		}

		@Override
		public EventValueRegistry unmodifiableView() {
			return this;
		}

	}

}
