package ch.njol.skript.registrations;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.expressions.base.EventValueExpression;
import ch.njol.util.Kleenean;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry.Resolution;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Use {@link EventValueRegistry} instead.
 * Obtain one using {@code Skript.instance().registry(EventValueRegistry.class)}.
 */
@Deprecated(since = "INSERT VERSION", forRemoval = true)
public class EventValues {

	private EventValues() {}

	/**
	 * The past value of an event value. Represented by "past" or "former".
	 * @deprecated Use {@link EventValue#TIME_PAST} instead.
	 */
	public static final int TIME_PAST = EventValue.TIME_PAST;

	/**
	 * The current time of an event value.
	 * @deprecated Use {@link EventValue#TIME_PAST} instead.
	 */
	public static final int TIME_NOW = EventValue.TIME_NOW;

	/**
	 * The future time of an event value.
	 * @deprecated Use {@link EventValue#TIME_PAST} instead.
	 */
	public static final int TIME_FUTURE = EventValue.TIME_FUTURE;

	private static EventValueRegistry registry;

	@ApiStatus.Internal
	public static void setEventValueRegistry(EventValueRegistry registry) {
		if (EventValues.registry != null)
			throw new IllegalStateException("EventValueRegistry is already set and cannot be changed.");
		EventValues.registry = registry;
	}

	/**
	 * Get Event Values list for the specified time
	 * @param time The time of the event values. One of
	 * {@link EventValues#TIME_PAST}, {@link EventValues#TIME_NOW} or {@link EventValues#TIME_FUTURE}.
	 * @return An immutable copy of the event values list for the specified time
	 * @deprecated Use {@link EventValueRegistry#elements(int)} instead.
	 */
	public static @Unmodifiable List<EventValueInfo<?, ?>> getEventValuesListForTime(int time) {
		//noinspection unchecked,rawtypes
		return (List) registry.elements(time).stream()
			.map(EventValueInfo::fromModern)
			.toList();
	}

	/**
	 * Registers an event value, specified by the provided {@link Converter}, with excluded events.
	 * Uses the default time, {@link #TIME_NOW}.
	 *
	 * @see #registerEventValue(Class, Class, Converter, int)
	 * @deprecated Use {@link EventValueRegistry#register(EventValue)} instead.
	 * Build an {@link EventValue} using {@link EventValue#builder(Class, Class)}.
	 */
	public static <T, E extends Event> void registerEventValue(
		Class<E> eventClass, Class<T> valueClass,
		Converter<E, T> converter
	) {
		registerEventValue(eventClass, valueClass, converter, TIME_NOW);
	}

	/**
	 * Registers an event value.
	 *
	 * @param eventClass the event class.
	 * @param valueClass the return type of the converter for the event value.
	 * @param converter the converter to get the value with the provided eventClass.
	 * @param time value of TIME_PAST if this is the value before the eventClass, TIME_FUTURE if after, and TIME_NOW if it's the default or this value doesn't have distinct states.
	 *            <b>Always register a default state!</b> You can leave out one of the other states instead, e.g. only register a default and a past state. The future state will
	 *            default to the default state in this case.
	 * @deprecated Use {@link EventValueRegistry#register(EventValue)} instead.
	 * Build an {@link EventValue} using {@link EventValue#builder(Class, Class)}.
	 */
	public static <T, E extends Event> void registerEventValue(
		Class<E> eventClass, Class<T> valueClass,
		Converter<E, T> converter, int time
	) {
		registerEventValue(eventClass, valueClass, converter, time, null, (Class<? extends E>[]) null);
	}

	/**
	 * Registers an event value and with excluded events.
	 * Excluded events are events that this event value can't operate in.
	 *
	 * @param eventClass the event class.
	 * @param valueClass the return type of the converter for the event value.
	 * @param converter the converter to get the value with the provided eventClass.
	 * @param time value of TIME_PAST if this is the value before the eventClass, TIME_FUTURE if after, and TIME_NOW if it's the default or this value doesn't have distinct states.
	 *            <b>Always register a default state!</b> You can leave out one of the other states instead, e.g. only register a default and a past state. The future state will
	 *            default to the default state in this case.
	 * @param excludeErrorMessage The error message to display when used in the excluded events.
	 * @param excludes subclasses of the eventClass for which this event value should not be registered for
	 * @deprecated Use {@link EventValueRegistry#register(EventValue)} instead.
	 * Build an {@link EventValue} using {@link EventValue#builder(Class, Class)}.
	 */
	@SafeVarargs
	public static <T, E extends Event> void registerEventValue(
		Class<E> eventClass, Class<T> valueClass,
		Converter<E, T> converter, int time,
		@Nullable String excludeErrorMessage,
		@Nullable Class<? extends E>... excludes
	) {
		EventValue.Builder<E, T> builder = EventValue.builder(eventClass, valueClass)
			.getter(converter)
			.time(time)
			.excludedErrorMessage(excludeErrorMessage)
			.excludes(excludes);
		if (converter instanceof EventConverter<E,T> eventConverter)
			builder.setChanger(eventConverter::set);
		registry.register(builder.build());
	}

	/**
	 * Gets a specific value from an eventClass. Returns null if the eventClass doesn't have such a value (conversions are done to try and get the desired value).
	 * <p>
	 *
	 * @param event eventClass
	 * @param valueClass return type of getter
	 * @param time -1 if this is the value before the eventClass, 1 if after, and 0 if it's the default or this value doesn't have distinct states.
	 *            <b>Always register a default state!</b> You can leave out one of the other states instead, e.g. only register a default and a past state. The future state will
	 *            default to the default state in this case.
	 * @return The event's value
	 * @see #registerEventValue(Class, Class, Converter, int)
	 */
	public static <T, E extends Event> @Nullable T getEventValue(E event, Class<T> valueClass, int time) {
		// TODO replace with a method in registry?
		//noinspection unchecked
		return registry.resolve(event.getClass(), valueClass, time).uniqueOptional()
			.map(eventValue -> ((EventValue<E, T>) eventValue).get(event))
			.orElse(null);
	}

	/**
	 * Checks that a {@link Converter} exists for the exact type. No converting or subclass checking.
	 *
	 * @param eventClass the event class the getter will be getting from
	 * @param valueClass type of {@link Converter}
	 * @param time the event-value's time
	 * @return A getter to get values for a given type of events
	 * @see #registerEventValue(Class, Class, Converter, int)
	 * @see EventValueExpression#EventValueExpression(Class)
	 * @deprecated Use {@link EventValueRegistry#resolveExact(Class, Class, int)} instead.
	 */
	@Nullable
	public static <E extends Event, T> Converter<? super E, ? extends T> getExactEventValueConverter(
		Class<E> eventClass, Class<T> valueClass, int time
	) {
		return registry.resolveExact(eventClass, valueClass, time).anyOptional()
			.map(EventValue::converter)
			.orElse(null);
	}

	/**
	 * Checks if an event has multiple {@link Converter}s, including default ones.
	 *
	 * @param eventClass the event class the {@link Converter} will be getting from.
	 * @param valueClass type of {@link Converter}.
	 * @param time the event-value's time.
	 * @return true or false if the event and type have multiple {@link Converter}s.
	 */
	public static <T, E extends Event> Kleenean hasMultipleConverters(Class<E> eventClass, Class<T> valueClass, int time) {
		List<Converter<? super E, ? extends T>> getters = getEventValueConverters(eventClass, valueClass, time, true, false);
		if (getters == null)
			return Kleenean.UNKNOWN;
		return Kleenean.get(getters.size() > 1);
	}

	/**
	 * Returns a {@link Converter} to get a value from in an event.
	 * <p>
	 * Can print an error if the event value is blocked for the given event.
	 *
	 * @param eventClass the event class the {@link Converter} will be getting from.
	 * @param valueClass type of {@link Converter}.
	 * @param time the event-value's time.
	 * @return A getter to get values for a given type of events.
	 * @see #registerEventValue(Class, Class, Converter, int)
	 * @see EventValueExpression#EventValueExpression(Class)
	 * @deprecated Use {@link EventValueRegistry#resolve(Class, Class, int)} instead.
	 */
	public static <T, E extends Event> @Nullable Converter<? super E, ? extends T> getEventValueConverter(
		Class<E> eventClass, Class<T> valueClass, int time
	) {
		return getEventValueConverter(eventClass, valueClass, time, true);
	}

	@Nullable
	private static <T, E extends Event> Converter<? super E, ? extends T> getEventValueConverter(
		Class<E> eventClass, Class<T> valueClass, int time, boolean allowDefault
	) {
		List<Converter<? super E, ? extends T>> list = getEventValueConverters(eventClass, valueClass, time, allowDefault);
		if (list == null || list.isEmpty())
			return null;
		return list.get(0);
	}

	@Nullable
	private static <T, E extends Event> List<Converter<? super E, ? extends T>> getEventValueConverters(
		Class<E> eventClass, Class<T> valueClass, int time, boolean allowDefault
	) {
		return getEventValueConverters(eventClass, valueClass, time, allowDefault, true);
	}

	/*
	 * We need to be able to collect all possible event-values to a list for determining problematic collisions.
	 * Always return after the loop check if the list is not empty.
	 */
	private static <T, E extends Event> @Nullable List<Converter<? super E, ? extends T>> getEventValueConverters(
		Class<E> eventClass, Class<T> valueClass, int time,
		boolean allowDefault, boolean allowConverting
	) {
		int flags = 0;
		if (allowDefault)
			flags |= EventValueRegistry.FALLBACK_TO_DEFAULT_TIME_STATE;
		if (allowConverting)
			flags |= EventValueRegistry.ALLOW_CONVERSION;
		Resolution<E, ? extends T> resolution = registry.resolve(eventClass, valueClass, time, flags);
		if (!resolution.successful())
			return null;
		//noinspection unchecked,rawtypes
		return (List) resolution.all().stream()
			.map(EventValue::converter)
			.toList();
	}

	/**
	 * <p>
	 *  In this method we can strip converters that are able to be obtainable through their own 'event-classinfo'.
	 *  For example, {@link PlayerTradeEvent} has a {@link Player} value (player who traded)
	 *  	and an {@link AbstractVillager} value (villager traded from).
	 *  Beforehand, since there is no {@link Entity} value, it was grabbing both values as they both can be casted as an {@link Entity},
	 *  	resulting in a parse error of "multiple entities".
	 * 	Now, we filter out the values that can be obtained using their own classinfo, such as 'event-player'
	 * 		which leaves us only the {@link AbstractVillager} for 'event-entity'.
	 * </p>
	 */
	private static <E extends Event, T> List<Converter<? super E, ? extends T>> stripConverters(
		Class<E> eventClass,
		Class<T> valueClass,
		Map<EventValueInfo<?, ?>, Converter<? super E, ? extends T>> infoConverterMap,
		List<Converter<? super E, ? extends T>> converters
	) {
		if (converters.size() == 1)
			return converters;
		ClassInfo<T> valueClassInfo = Classes.getExactClassInfo(valueClass);
		List<Converter<? super E, ? extends T>> stripped = new ArrayList<>();
		for (EventValueInfo<?, ?> eventValueInfo : infoConverterMap.keySet()) {
			ClassInfo<?> thisClassInfo = Classes.getExactClassInfo(eventValueInfo.valueClass);
			if (thisClassInfo != null && !thisClassInfo.equals(valueClassInfo))
				continue;
			stripped.add(infoConverterMap.get(eventValueInfo));
		}
		if (stripped.isEmpty())
			return converters;
		return stripped;
	}

	/**
	 * Check if the event value states to exclude events.
	 * False if the current EventValueInfo cannot operate in the provided eventClass.
	 *
	 * @param info The event value info that will be used to grab the value from
	 * @param eventClass The event class to check the excludes against.
	 * @return boolean if true the event value passes for the events.
	 */
	private static boolean checkExcludes(EventValueInfo<?, ?> info, Class<? extends Event> eventClass) {
		if (info.excludes == null)
			return true;
		for (Class<? extends Event> ex : (Class<? extends Event>[]) info.excludes) {
			if (ex.isAssignableFrom(eventClass)) {
				Skript.error(info.excludeErrorMessage);
				return false;
			}
		}
		return true;
	}

	/**
	 * Return a converter wrapped in a getter that will grab the requested value by converting from the given event value info.
	 *
	 * @param info The event value info that will be used to grab the value from
	 * @param valueClass The class that the converter will look for to convert the type from the event value to
	 * @param checkInstanceOf If the eventClass must be an exact instance of the event value info's eventClass or not.
	 * @return The found Converter wrapped in a Getter object, or null if no Converter was found.
	 */
	@Nullable
	private static <E extends Event, F, T> Converter<? super E, ? extends T> getConvertedConverter(
		EventValueInfo<E, F> info, Class<T> valueClass, boolean checkInstanceOf
	) {
		Converter<? super F, ? extends T> converter = Converters.getConverter(info.valueClass, valueClass);

		if (converter == null)
			return null;

		return event -> {
			if (checkInstanceOf && !info.eventClass.isInstance(event))
				return null;
			F f = info.converter.convert(event);
			if (f == null)
				return null;
			return converter.convert(f);
		};
	}

	public static boolean doesExactEventValueHaveTimeStates(Class<? extends Event> eventClass, Class<?> valueClass) {
		return getExactEventValueConverter(eventClass, valueClass, TIME_PAST) != null
			|| getExactEventValueConverter(eventClass, valueClass, TIME_FUTURE) != null;
	}

	public static boolean doesEventValueHaveTimeStates(Class<? extends Event> eventClass, Class<?> valueClass) {
		return getEventValueConverter(eventClass, valueClass, TIME_PAST, false) != null
			|| getEventValueConverter(eventClass, valueClass, TIME_FUTURE, false) != null;
	}

	/**
	 * All supported time states for an event value.
	 * @return An array of all the time states.
	 */
	public static int[] getTimeStates() {
		return new int[] {TIME_PAST, TIME_NOW, TIME_FUTURE};
	}

	/**
	 * @return All the event values for each registered event's class.
	 */
	public static Multimap<Class<? extends Event>, EventValueInfo<?, ?>> getPerEventEventValues() {
		Multimap<Class<? extends Event>, EventValueInfo<?, ?>> eventValues = MultimapBuilder
			.hashKeys()
			.hashSetValues()
			.build();

		for (int time : getTimeStates()) {
			for (EventValueInfo<?, ?> eventValueInfo : getEventValuesListForTime(time)) {
				Collection<EventValueInfo<?, ?>> existing = eventValues.get(eventValueInfo.eventClass);
				existing.add(eventValueInfo);
				eventValues.putAll(eventValueInfo.eventClass, existing);
			}
		}
		return eventValues;
	}

	public record EventValueInfo<E extends Event, T>(
		Class<E> eventClass, Class<T> valueClass, Converter<E, T> converter,
		@Nullable String excludeErrorMessage,
		@Nullable Class<? extends E>[] excludes, int time
	) {
		public EventValueInfo {
			assert eventClass != null;
			assert valueClass != null;
			assert converter != null;
		}

		public static <E extends Event, T> EventValueInfo<E, T> fromModern(EventValue<E, T> eventValue) {
			return new EventValueInfo<>(
				eventValue.eventClass(),
				eventValue.valueClass(),
				eventValue.changer(ChangeMode.SET)
					.map(changer -> (Converter<E, T>) new EventConverter<E, T>() {
						@Override
						public @Nullable T convert(E from) {
							return eventValue.get(from);
						}

						@Override
						public void set(E event, @Nullable T value) {
							changer.change(event, value);
						}
					})
					.orElse(eventValue.converter()),
				eventValue.excludedErrorMessage(),
				eventValue.excludedEvents(),
				eventValue.time()
			);
		}

	}

}
