package org.skriptlang.skript.bukkit.text;

import ch.njol.skript.SkriptConfig;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.text.elements.*;
import org.skriptlang.skript.lang.arithmetic.Arithmetics;
import org.skriptlang.skript.lang.arithmetic.Operator;
import org.skriptlang.skript.lang.comparator.Comparators;
import org.skriptlang.skript.lang.comparator.Relation;
import org.skriptlang.skript.lang.converter.Converters;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Locale;
import java.util.regex.Pattern;

public class TextModule implements AddonModule {

	@Override
	public void init(SkriptAddon addon) {
		Classes.registerClass(new ClassInfo<>(Component.class, "textcomponent")
			.user("text ?components?")
			.name("Text Component")
			.description("Text components are used to represent how text is displayed in Minecraft.",
				"This includes colors, decorations, and more.")
			.examples("\"<red><bold>This text is red and bold!\"")
			.since("INSERT VERSION")
			.parser(new Parser<>() {
				@Override
				public boolean canParse(ParseContext context) {
					return false;
				}

				@Override
				public String toString(Component component, int flags) {
					return TextComponentParser.instance().toString(component);
				}

				@Override
				public String toVariableNameString(Component component) {
					return "textcomponent:" + component;
				}
			})
		);

		Converters.registerConverter(String.class, Component.class,
			string -> TextComponentParser.instance().parse(string));
		Converters.registerConverter(Component.class, String.class,
			component -> TextComponentParser.instance().toString(component));

		TextReplacementConfig componentToLowercase = TextReplacementConfig.builder()
			.match(Pattern.compile(".+", Pattern.DOTALL))
			.replacement(text -> text.content(text.content().toLowerCase(Locale.ENGLISH)))
			.build();
		Comparators.registerComparator(Component.class, String.class, (component, string) -> {
			if (!SkriptConfig.caseSensitive.value()) {
				component = component.replaceText(componentToLowercase);
				string = string.toLowerCase(Locale.ENGLISH);
			}
			return Relation.get(component.equals(TextComponentParser.instance().parse(string)));
		});

		Arithmetics.registerOperation(Operator.ADDITION, Component.class, Component.class, Component::append);
		Arithmetics.registerOperation(Operator.ADDITION, Component.class, String.class,
			(component, string) -> component.append(TextComponentParser.instance().parse(string)),
			(string, component) -> TextComponentParser.instance().parse(string).append(component));
	}

	@Override
	public void load(SkriptAddon addon) {
		// register syntax
		SyntaxRegistry syntaxRegistry = addon.syntaxRegistry();
		EffActionBar.register(syntaxRegistry);
		EffBroadcast.register(syntaxRegistry);
		EffMessage.register(syntaxRegistry);
		EffResetTitle.register(syntaxRegistry);
		EffSendTitle.register(syntaxRegistry);
		ExprColored.register(syntaxRegistry);
		ExprRawString.register(syntaxRegistry);
		ExprStringColor.register(syntaxRegistry);
	}

}
