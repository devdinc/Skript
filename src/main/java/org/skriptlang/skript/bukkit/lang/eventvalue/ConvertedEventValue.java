package org.skriptlang.skript.bukkit.lang.eventvalue;

import ch.njol.skript.classes.Changer.ChangeMode;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
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
	private final Converter<SourceValue, ConvertedValue> converter;
	private final @Nullable Converter<ConvertedValue, SourceValue> reverseConverter;

    public static <SourceEvent extends Event, ConvertedEvent extends Event, SourceValue, ConvertedValue> EventValue<ConvertedEvent, ConvertedValue> newInstance(
		Class<ConvertedEvent> eventClass,
		Class<ConvertedValue> valueClass,
		EventValue<SourceEvent, SourceValue> source
	) {
		if (source.eventClass().isAssignableFrom(eventClass) && valueClass.isAssignableFrom(source.valueClass()))
			//noinspection unchecked
			return (EventValue<ConvertedEvent, ConvertedValue>) source;
		Converter<SourceValue, ConvertedValue> converter = getConverter(source.valueClass(), valueClass);
		if (converter == null)
			return null;
		return new ConvertedEventValue<>(
			eventClass,
			valueClass,
			source,
			converter,
			getConverter(valueClass, source.valueClass())
		);
	}

	private static <F, T> Converter<F, T> getConverter(Class<F> from, Class<T> to) {
		//noinspection unchecked
		return to.isAssignableFrom(from) ? value -> (T) value : Converters.getConverter(from, to);
	}

	public ConvertedEventValue(
		Class<ConvertedEvent> eventClass,
		Class<ConvertedValue> valueClass,
		EventValue<SourceEvent, SourceValue> source,
		Converter<SourceValue, ConvertedValue> converter,
		@Nullable Converter<ConvertedValue, SourceValue> reverseConverter
	) {
		this.eventClass = eventClass;
		this.valueClass = valueClass;
		this.source = source;
		this.converter = converter;
		this.reverseConverter = reverseConverter == null
			? getConverter(valueClass, source.valueClass())
			: reverseConverter;
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
		return event -> {
			if (!source.eventClass().isAssignableFrom(event.getClass()))
				return null;
			SourceValue sourceValue = source.get(source.eventClass().cast(event));
			return converter.convert(sourceValue);
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
			if (reverseConverter == null)
				return;
			SourceValue sourceValue = reverseConverter.convert(value);
			if (sourceValue != null)
				changer.change(source.eventClass().cast(event), sourceValue);
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
	public @Nullable <NewEvent extends Event, NewValue> EventValue<NewEvent, NewValue> getConverted(
		Class<NewEvent> newEventClass,
		Class<NewValue> newValueClass
	) {
		return ConvertedEventValue.newInstance(newEventClass, newValueClass, source);
	}

	@Override
	public <NewEvent extends Event, NewValue> EventValue<NewEvent, NewValue> getConverted(
		Class<NewEvent> newEventClass,
		Class<NewValue> newValueClass,
		Converter<ConvertedValue, NewValue> converter,
		@Nullable Converter<NewValue, ConvertedValue> reverseConverter
	) {
		return new ConvertedEventValue<>(newEventClass, newValueClass, this, converter, reverseConverter);
	}

}
