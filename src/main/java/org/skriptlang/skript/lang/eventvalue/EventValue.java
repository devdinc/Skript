package org.skriptlang.skript.lang.eventvalue;

import ch.njol.skript.classes.Changer.ChangeMode;
import org.bukkit.event.Event;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.converter.Converter;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Describes a single "event value" available in a specific {@link org.bukkit.event.Event} context.
 * An event value provides a typed value (e.g. the player, entity, location) for a given event and
 * can optionally support changing that value via Skript's {@link ch.njol.skript.classes.Changer} API.
 * <p>
 * Each event value is identified by one or more textual identifier patterns that are matched against
 * user input (e.g. {@code "player"}, {@code "entity"}). Resolution and lookup are handled by
 * {@link EventValueRegistry}.
 * <p>
 * Instances should be created using {@link #builder(Class, Class)} and registered via
 * {@link EventValueRegistry#register(EventValue)}.
 */
public sealed interface EventValue<E extends Event, V> permits EventValueImpl, ConvertedEventValue {

	/**
	 * Creates a new builder for an {@link EventValue}.
	 *
	 * @param eventClass the event type this value applies to
	 * @param valueClass the value type to produce
	 * @return a builder to configure and build the event value
	 */
	static <E extends Event, V> EventValueImpl.Builder<E, V> builder(Class<E> eventClass, Class<V> valueClass) {
		return new EventValueImpl.BuilderImpl<>(eventClass, valueClass);
	}

	/**
	 * The past value of an event value.
	 */
	int TIME_PAST = -1;

	/**
	 * The current time of an event value.
	 */
	int TIME_NOW = 0;

	/**
	 * The future time of an event value.
	 */
	int TIME_FUTURE = 1;

	/**
	 * The event class this value applies to.
	 *
	 * @return the event type this event value is defined for
	 */
	Class<E> eventClass();

	/**
	 * The type of the value produced by this event value.
	 *
	 * @return the value type
	 */
	Class<V> valueClass();

	/**
	 * Patterns used to identify this event value from user input.
	 * These are regex patterns and are checked in order during resolution.
	 *
	 * @return the identifier patterns
	 */
	Pattern[] identifierPatterns();

	/**
	 * Validates that this event value can be used in the provided event context.
	 *
	 * @param event the concrete event class to validate against
	 * @return {@code true} if this event value can be used for the given event class
	 */
	boolean validate(Class<?> event);

	/**
	 * Checks whether the provided input matches one of {@link #identifierPatterns()} and
	 * satisfies any additional input validation.
	 *
	 * @param input the identifier provided by the user
	 * @return {@code true} if the validation succeeds
	 */
	boolean matchesInput(String input);

	/**
	 * Obtains the value from the given event instance.
	 *
	 * @param event the event instance
	 * @return the value obtained from the event, which may be {@code null}
	 */
	V get(E event);

	/**
	 * The converter used to obtain the value from the event.
	 *
	 * @return the converter
	 */
	Converter<E, V> converter();

	/**
	 * Checks whether a changer is supported for the specified {@link ChangeMode}.
	 *
	 * @param mode the change mode
	 * @return {@code true} if a changer is supported
	 */
	boolean hasChanger(ChangeMode mode);

	/**
	 * Returns the changer for the specified {@link ChangeMode}, if present.
	 *
	 * @param mode the change mode
	 * @return an {@link Optional} containing the changer if available
	 */
	Optional<Changer<E, V>> changer(ChangeMode mode);

	/**
	 * The time state this event value is registered for.
	 * One of {@link #TIME_PAST}, {@link #TIME_NOW}, or {@link #TIME_FUTURE}.
	 *
	 * @return the time state
	 */
	int time();

	/**
	 * Event types explicitly excluded from using this event value.
	 *
	 * @return an array of excluded event classes or {@code null} if none
	 */
	Class<? extends E> @Nullable [] excludedEvents();

	/**
	 * An optional error message shown when this value is excluded for a matching event.
	 *
	 * @return the exclusion error message or {@code null}
	 */
	@Nullable String excludedErrorMessage();

	/**
	 * Creates a view of this event value for a different (related) event class.
	 *
	 * @param newEventClass the target event type
	 * @param <U> the new event type parameter
	 * @return an event value usable with the given event class
	 */
	<U extends Event> EventValue<U, V> forEventClass(Class<U> newEventClass);

	/**
	 * Creates a converted view of this event value that yields a different value type.
	 * Conversion is performed using {@link org.skriptlang.skript.lang.converter.Converters} at access time.
	 *
	 * @param newValueClass the desired value class
	 * @param <U> the new value type parameter
	 * @return an event value that converts results to the requested type, or null if conversion is not possible
	 */
	<U> EventValue<E, U> convertedTo(Class<U> newValueClass);

	@FunctionalInterface
	interface Changer<E, V> {

		/**
		 * Applies a change to the value for the given event instance.
		 *
		 * @param event the event instance
		 * @param value the value to apply (may be {@code null} depending on mode)
		 */
		void change(E event, V value);

	}

	@FunctionalInterface
	interface NoValueChanger<E, V> extends Changer<E, V> {

		/**
		 * Applies a change that does not require a value (e.g. {@link ChangeMode#RESET} or {@link ChangeMode#DELETE}).
		 *
		 * @param event the event instance
		 */
		void change(E event);

		@Override
		default void change(E event, V value) {
			change(event);
		}

	}

	interface Builder<E extends Event, V> {

		/**
		 * Adds one or more regex identifier patterns matched against user input.
		 *
		 * @param identifierPattern regex patterns
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> identifierPattern(@RegExp String... identifierPattern);

		/**
		 * Sets an additional validator that must accept the input for this value to match.
		 *
		 * @param inputValidator predicate invoked after pattern match
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> inputValidator(Predicate<String> inputValidator);

		/**
		 * Sets an event-type validator that must accept the event class for this value to be valid.
		 *
		 * @param eventValidator predicate to validate event classes
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> eventValidator(Predicate<Class<?>> eventValidator);

		/**
		 * Sets the converter used to obtain the value from the event.
		 *
		 * @param converter the value converter
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> getter(Converter<E, V> converter);

		/**
		 * Registers a changer for {@link ChangeMode#SET}.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> setChanger(Changer<E, V> changer);

		/**
		 * Registers a changer for {@link ChangeMode#ADD}.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> addChanger(Changer<E, V> changer);

		/**
		 * Registers a changer for {@link ChangeMode#REMOVE}.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> removeChanger(Changer<E, V> changer);

		/**
		 * Registers a changer for {@link ChangeMode#REMOVE_ALL}.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> removeAllChanger(Changer<E, V> changer);

		/**
		 * Registers a changer for {@link ChangeMode#DELETE} that does not require a value.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> deleteChanger(NoValueChanger<E, V> changer);

		/**
		 * Registers a changer for {@link ChangeMode#RESET} that does not require a value.
		 *
		 * @param changer the changer implementation
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> resetChanger(NoValueChanger<E, V> changer);

		/**
		 * Sets the time state for which this event value is registered.
		 *
		 * @param time one of {@link #TIME_PAST}, {@link #TIME_NOW} or {@link #TIME_FUTURE}
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		Builder<E,V> time(int time);

		/**
		 * Excludes specific event subclasses from using this event value.
		 *
		 * @param events event classes to exclude
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		EventValueImpl.Builder<E, V> excludes(Class<? extends E>... events);

		/**
		 * Sets an error message to be shown if this event value is selected for an excluded event.
		 *
		 * @param excludedErrorMessage the message to display
		 * @return this builder
		 */
		@Contract(value = "_ -> this", mutates = "this")
		EventValueImpl.Builder<E, V> excludedErrorMessage(String excludedErrorMessage);

		/**
		 * Builds the event value.
		 *
		 * @return the constructed event value instance
		 */
		EventValue<E, V> build();

	}

}
