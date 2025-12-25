package org.skriptlang.skript.bukkit.lang.eventvalue;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptAPIException;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Unmodifiable;
import org.skriptlang.skript.lang.converter.Converters;
import org.skriptlang.skript.util.Registry;
import org.skriptlang.skript.util.ViewProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registry and resolver for {@link EventValue} definitions.
 * <p>
 * Use this registry to register, unregister and resolve event values by identifier text or by
 * desired value type. Resolution prefers the closest matching event type and, optionally,
 * can fall back to default time state and/or allow value-type conversion.
 * <p>
 * Obtain an instance using {@code SkriptAddon#registry(EventValueRegistry.class)}.
 * <br>
 * Or an unmodifiable view using {@code Skript.instance().registry(EventValueRegistry.class)}.
 */
public interface EventValueRegistry extends Registry<EventValue<?, ?>>, ViewProvider<EventValueRegistry> {

	/**
	 * Creates an empty event value registry.
	 */
	static EventValueRegistry empty(Skript skript) {
		return new EventValueRegistryImpl(skript);
	}

	/**
	 * Fallback to {@link EventValue.Time#NOW} if no value is found for the requested time.
	 */
	int FALLBACK_TO_DEFAULT_TIME_STATE = 0b01;
	/**
	 * Allow converting the resolved value type using {@link Converters}
	 */
	int ALLOW_CONVERSION = 0b10;
	/**
	 * Default combination of resolution flags: fallback to default time and allow conversion.
	 */
	int DEFAULT_RESOLVE_FLAGS = FALLBACK_TO_DEFAULT_TIME_STATE | ALLOW_CONVERSION;

	/**
	 * Registers a new {@link EventValue}.
	 *
	 * @throws SkriptAPIException if another value with the same
	 * event class, time, and identifier patterns already exists
	 */
	<E extends Event> void register(EventValue<E, ?> eventValue);

	/**
	 * Unregisters the given event value.
	 *
	 * @return {@code true} if the value was removed
	 */
	boolean unregister(EventValue<?, ?> eventValue);

	/**
	 * Checks whether an equivalent event value is already registered.
	 */
	boolean isRegistered(EventValue<?, ?> eventValue);

	/**
	 * Checks whether a value for the exact event/value class and time is registered.
	 */
	boolean isRegistered(Class<? extends Event> eventClass, Class<?> valueClass, EventValue.Time time);

	/**
	 * Resolve an {@link EventValue} by identifier using {@link EventValue.Time#NOW} and {@link #DEFAULT_RESOLVE_FLAGS}.
	 *
	 * @see #resolve(Class, String, EventValue.Time)
	 */
	<E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier);

	/**
	 * Resolve an {@link EventValue} by identifier for a specific time using {@link #DEFAULT_RESOLVE_FLAGS}.
	 *
	 * @see #resolve(Class, String, EventValue.Time, int)
	 */
	<E extends Event, V> Resolution<E, V> resolve(Class<E> eventClass, String identifier, EventValue.Time time);

	/**
	 * Resolve an {@link EventValue} by identifier with explicit time and flags.
	 *
	 * @param eventClass the event type to resolve for
	 * @param identifier user input that identifies the value
	 * @param time the time state
	 * @param flags bitwise OR of {@link #FALLBACK_TO_DEFAULT_TIME_STATE} and {@link #ALLOW_CONVERSION}
	 * @return a {@link Resolution} describing candidates or empty/error state
	 */
	<E extends Event, V> Resolution<E, V> resolve(
		Class<E> eventClass,
		String identifier,
		EventValue.Time time,
		int flags
	);

	/**
	 * Resolves by desired value class using {@link EventValue.Time#NOW} and {@link #DEFAULT_RESOLVE_FLAGS}.
	 */
	<E extends Event, V> Resolution<E, ? extends V> resolve(Class<E> eventClass, Class<V> valueClass);

	/**
	 * Resolves by desired value class for a specific time using {@link #DEFAULT_RESOLVE_FLAGS}.
	 */
	<E extends Event, V> Resolution<E, ? extends V> resolve(
		Class<E> eventClass,
		Class<V> valueClass,
		EventValue.Time time
	);

	/**
	 * Resolves by desired value class with explicit time and flags.
	 *
	 * @param eventClass the event type to resolve for
	 * @param valueClass the desired value type
	 * @param time the time state
	 * @param flags bitwise OR of {@link #FALLBACK_TO_DEFAULT_TIME_STATE} and {@link #ALLOW_CONVERSION}
	 */
	<E extends Event, V> Resolution<E, ? extends V> resolve(
		Class<E> eventClass,
		Class<V> valueClass,
		EventValue.Time time,
		int flags
	);

	/**
	 * Resolves only exact value-class matches, choosing the nearest compatible event class.
	 */
	<E extends Event, V> Resolution<E, V> resolveExact(
		Class<E> eventClass,
		Class<V> valueClass,
		EventValue.Time time
	);

	/**
	 * Returns all registered event values at all time states.
	 */
	@Override
	@Unmodifiable
	List<EventValue<?, ?>> elements();

	/**
	 * Returns a snapshot of event values for the given time state.
	 *
	 * @param time the time state
	 */
	@Unmodifiable
	List<EventValue<?, ?>> elements(EventValue.Time time);

	@Override
	EventValueRegistry unmodifiableView();

	/**
	 * Result of a registry resolve operation. May contain multiple candidates or be empty.
	 * When {@link #errored()} is {@code true}, at least one candidate failed validation
	 * and no result should be used.
	 */
	record Resolution<E extends Event, V>(List<EventValue<E, V>> all, boolean errored) {

		/**
		 * Creates a successful resolution from candidates.
		 */
		public static <E extends Event, V> Resolution<E, V> of(List<EventValue<E, V>> eventValues) {
			return new Resolution<>(eventValues, false);
		}

		/**
		 * Creates an empty, non-error resolution.
		 */
		public static <E extends Event, V> Resolution<E, V> empty() {
			return new Resolution<>(Collections.emptyList(), false);
		}

		/**
		 * Creates an error resolution (e.g., a candidate failed validation).
		 */
		public static <E extends Event, V> Resolution<E, V> error() {
			return new Resolution<>(Collections.emptyList(), true);
		}

		/**
		 * @return {@code true} if at least one candidate exists
		 */
		public boolean successful() {
			return !all.isEmpty();
		}

		/**
		 * @return {@code true} if multiple candidates are available
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
		 * @return the single candidate or {@code null} if not unique
		 */
		public EventValue<E, V> uniqueOrNull() {
			if (all.size() != 1)
				return null;
			return all.getFirst();
		}

		/**
		 * @return the single candidate as an {@link Optional}, empty if not unique
		 */
		public Optional<EventValue<E, V>> uniqueOptional() {
			if (all.size() != 1)
				return Optional.empty();
			return Optional.of(all.getFirst());
		}

		/**
		 * @return any candidate, throws if none
		 * @throws IllegalStateException if the resolution is empty
		 */
		public EventValue<E, V> any() {
			if (all.isEmpty())
				throw new IllegalStateException("Resolution is empty");
			return all.getFirst();
		}

		/**
		 * @return any candidate or {@code null} if none
		 */
		public EventValue<E, V> anyOrNull() {
			if (all.isEmpty())
				return null;
			return all.getFirst();
		}

		/**
		 * @return any candidate as an {@link Optional}, empty if none
		 */
		public Optional<EventValue<E, V>> anyOptional() {
			if (all.isEmpty())
				return Optional.empty();
			return Optional.of(all.getFirst());
		}

		/**
		 * @return number of candidates contained
		 */
		public int size() {
			return all.size();
		}

	}

}
