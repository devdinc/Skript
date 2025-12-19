package org.skriptlang.skript.lang.eventvalue;

import ch.njol.skript.classes.Changer.ChangeMode;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

final class ConvertedEventValue<SourceEvent extends Event, ConvertedEvent extends Event, SourceValue, ConvertedValue>
	implements EventValue<ConvertedEvent, ConvertedValue> {

	private final Class<ConvertedEvent> eventClass;
	private final Class<ConvertedValue> valueClass;
	private final EventValue<SourceEvent, SourceValue> source;

    public static <SourceEvent extends Event, ConvertedEvent extends Event, SourceValue, ConvertedValue> EventValue<ConvertedEvent, ConvertedValue> newInstance(
		Class<ConvertedEvent> eventClass,
		Class<ConvertedValue> valueClass,
		EventValue<SourceEvent, SourceValue> source
	) {
		if (source.eventClass().isAssignableFrom(eventClass) && valueClass.isAssignableFrom(source.valueClass()))
			//noinspection unchecked
			return (EventValue<ConvertedEvent, ConvertedValue>) source;
		if (!Converters.converterExists(source.valueClass(), valueClass))
			return null;
		return new ConvertedEventValue<>(eventClass, valueClass, source);
	}

	private ConvertedEventValue(
		Class<ConvertedEvent> eventClass,
		Class<ConvertedValue> valueClass,
		EventValue<SourceEvent, SourceValue> source
	) {
		this.eventClass = eventClass;
		this.valueClass = valueClass;
		this.source = source;
	}

	@Override
	public Class<ConvertedEvent> eventClass() {
		return eventClass;
	}

	@Override
	public Class<ConvertedValue> valueClass() {
		return valueClass;
	}

	@Override
	public Pattern[] identifierPatterns() {
		return source.identifierPatterns();
	}

	@Override
	public boolean validate(Class<?> event) {
		return source.validate(event);
	}

	@Override
	public boolean matchesInput(String input) {
		return source.matchesInput(input);
	}

	@Override
	public ConvertedValue get(ConvertedEvent event) {
		return converter().convert(event);
	}

	@Override
	public Converter<ConvertedEvent, ConvertedValue> converter() {
		return new Converter<>() {
			@Override
			public @Nullable ConvertedValue convert(ConvertedEvent event) {
				if (!source.eventClass().isAssignableFrom(event.getClass()))
					return null;
				SourceValue sourceValue = source.get(source.eventClass().cast(event));
				return Converters.convert(sourceValue, valueClass);
			}
		};
	}

	@Override
	public boolean hasChanger(ChangeMode mode) {
		return source.hasChanger(mode);
	}

	@Override
	public Optional<Changer<ConvertedEvent, ConvertedValue>> changer(ChangeMode mode) {
		return source.changer(mode).map(changer -> (event, value) -> {
			if (!source.eventClass().isAssignableFrom(event.getClass()))
				return;
			if (changer instanceof EventValue.NoValueChanger) {
				changer.change(source.eventClass().cast(event), null);
				return;
			}
			SourceValue convert = Converters.convert(value, source.valueClass());
			if (convert != null)
				changer.change(source.eventClass().cast(event), convert);
		});
	}

	@Override
	public int time() {
		return source.time();
	}

	@Override
	public Class<? extends ConvertedEvent> @Nullable [] excludedEvents() {
		Class<? extends SourceEvent>[] excludedEvents = source.excludedEvents();
		if (excludedEvents == null)
			return null;
		//noinspection unchecked
		return Arrays.stream(excludedEvents)
			.filter(eventClass::isAssignableFrom)
			.toArray(Class[]::new);
	}

	@Override
	public @Nullable String excludedErrorMessage() {
		return source.excludedErrorMessage();
	}

	@Override
	public <U extends Event> EventValue<U, ConvertedValue> forEventClass(Class<U> newEventClass) {
		return newInstance(newEventClass, valueClass, source);
	}

	@Override
	public <U> EventValue<ConvertedEvent, U> convertedTo(Class<U> newValueClass) {
		return newInstance(eventClass, newValueClass, source);
	}

}
