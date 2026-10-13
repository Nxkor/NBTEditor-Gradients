package com.imnot.nbteditorgradients;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

/**
 * Turns a colour code into coloured text, whatever its format: MiniMessage, &amp;#rrggbb, #rrggbb, JSON,
 * §x§r§r§g§g§b§b, &amp;x&amp;r&amp;r&amp;g&amp;g&amp;b&amp;b, &lt;#rrggbb&gt;, [COLOR=#rrggbb]. The formats can be mixed in one text.
 * The text is not italic unless the code says so.
 */
public final class ColorText {
	/** What an item name or a lore line starts from: Minecraft draws them italic when nothing is said. */
	private static final Style BASE = Style.EMPTY.withItalic(false);

	/** A tag still open: the style to come back to when it closes, and where its gradient starts. */
	private record Open(String name, Style saved, int start, List<TextColor> stops) {}

	private final List<Integer> chars = new ArrayList<>();
	private final List<Style> styles = new ArrayList<>();
	private final List<Open> open = new ArrayList<>();
	private Style cur = BASE;
	/** The style each char of the text already has, when it comes from a text field; null for a text typed in a command. */
	private Style[] source;
	private int at;

	private ColorText() {}

	public static Component parse(String text, HolderLookup.Provider registries) {
		return parse(text, null, registries);
	}

	/** Reads the codes written in a text that is already styled: what no code covers stays as it was. */
	public static Component convert(Component text, HolderLookup.Provider registries) {
		List<Style> styles = new ArrayList<>();
		text.visit((style, part) -> {
			for (int n = 0; n < part.length(); n++) styles.add(style);
			return Optional.empty();
		}, Style.EMPTY);
		return parse(text.getString(), styles.toArray(Style[]::new), registries);
	}

	private static Component parse(String text, Style[] source, HolderLookup.Provider registries) {
		String trimmed = text.trim();
		if (trimmed.startsWith("{") || trimmed.startsWith("[{") || trimmed.startsWith("[\"")) {
			try {
				MutableComponent json = ComponentSerialization.CODEC
					.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(trimmed)).getOrThrow().copy();
				return json.getStyle().isItalic() ? json : json.setStyle(json.getStyle().withItalic(false));
			} catch (RuntimeException notJson) {
				// a text that only looks like JSON: read it as a colour code
			}
		}
		ColorText parser = new ColorText();
		parser.source = source;
		parser.read(text);
		return build(parser.chars, parser.styles);
	}

	private void read(String s) {
		for (int i = 0; i < s.length(); ) {
			char c = s.charAt(i);
			at = i;
			if (c == '\\' && i + 1 < s.length() && "\\<[&#§".indexOf(s.charAt(i + 1)) >= 0) {
				at = i + 1;
				add(s.charAt(i + 1));
				i += 2;
				continue;
			}
			if (c == '&' || c == '§') {
				int end = legacy(s, i);
				if (end > i) {
					i = end;
					continue;
				}
			}
			if (c == '#' && isHex(s, i + 1, 6)) {
				cur = BASE.withColor(Integer.parseInt(s.substring(i + 1, i + 7), 16));
				i += 7;
				continue;
			}
			if (c == '<' || c == '[') {
				int close = s.indexOf(c == '<' ? '>' : ']', i);
				if (close > i && tag(s.substring(i + 1, close).toLowerCase())) {
					i = close + 1;
					continue;
				}
			}
			int cp = s.codePointAt(i);
			add(cp);
			i += Character.charCount(cp);
		}
		while (!open.isEmpty()) close(open.size() - 1);
	}

	/** Reads &amp;#rrggbb, &amp;x&amp;r&amp;r&amp;g&amp;g&amp;b&amp;b or &amp;l at {@code i}; returns the index after it, or {@code i} when it is plain text. */
	private int legacy(String s, int i) {
		if (i + 1 >= s.length()) return i;
		char code = Character.toLowerCase(s.charAt(i + 1));
		if (code == '#' && isHex(s, i + 2, 6)) {
			cur = BASE.withColor(Integer.parseInt(s.substring(i + 2, i + 8), 16));
			return i + 8;
		}
		if (code == 'x' && i + 14 <= s.length()) {
			StringBuilder hex = new StringBuilder();
			for (int k = i + 2; k < i + 14; k += 2) {
				if ((s.charAt(k) == '&' || s.charAt(k) == '§') && isHex(s, k + 1, 1)) hex.append(s.charAt(k + 1));
			}
			if (hex.length() == 6) {
				cur = BASE.withColor(Integer.parseInt(hex.toString(), 16));
				return i + 14;
			}
		}
		ChatFormatting format = ChatFormatting.getByCode(code);
		if (format == null) return i;
		// as on a server: a colour ends the bold, italic... before it
		if (format == ChatFormatting.RESET) cur = BASE;
		else if (TextColor.fromLegacyFormat(format) != null) cur = BASE.withColor(format);
		else cur = cur.applyFormat(format);
		return i + 2;
	}

	/** Applies the inside of a &lt;tag&gt; or [TAG]; false when it is not one, the text is then kept as written. */
	private boolean tag(String body) {
		if (body.startsWith("/")) {
			String name = name(body.substring(1).split("[:=]")[0]);
			for (int k = open.size() - 1; k >= 0; k--) {
				if (open.get(k).name.equals(name)) {
					close(k);
					return true;
				}
			}
			return false;
		}
		boolean off = body.startsWith("!");
		String[] parts = (off ? body.substring(1) : body).split("[:=]");
		String name = name(parts[0]);
		Style before = cur;
		List<TextColor> stops = null;
		switch (name) {
			case "reset" -> {
				while (!open.isEmpty()) close(open.size() - 1);
				cur = BASE;
				return true;
			}
			case "bold" -> cur = cur.withBold(on(off, parts));
			case "italic" -> cur = cur.withItalic(on(off, parts));
			case "underlined" -> cur = cur.withUnderlined(on(off, parts));
			case "strikethrough" -> cur = cur.withStrikethrough(on(off, parts));
			case "obfuscated" -> cur = cur.withObfuscated(on(off, parts));
			case "color" -> {
				// <#rrggbb>, <red>, <color:#rrggbb>, [color=#rrggbb]
				TextColor color = color(parts[parts.length - 1]);
				if (color == null) return false;
				cur = cur.withColor(color);
			}
			case "gradient" -> {
				stops = new ArrayList<>();
				for (int k = 1; k < parts.length; k++) {
					TextColor color = color(parts[k]);
					if (color != null) stops.add(color); // what is not a colour is the phase: not handled
				}
				if (stops.isEmpty()) stops = List.of(TextColor.fromRgb(0xFFFFFF), TextColor.fromRgb(0x000000));
				cur = cur.withColor((TextColor) null);
			}
			case "rainbow" -> {
				stops = List.of();
				cur = cur.withColor((TextColor) null);
			}
			default -> {
				return false;
			}
		}
		open.add(new Open(name, before, chars.size(), stops));
		return true;
	}

	private static String name(String tag) {
		return switch (tag) {
			case "b", "bold" -> "bold";
			case "i", "em", "italic" -> "italic";
			case "u", "underline", "underlined" -> "underlined";
			case "s", "st", "strikethrough" -> "strikethrough";
			case "obf", "obfuscated" -> "obfuscated";
			case "c", "color", "colour" -> "color";
			case "gradient", "rainbow", "reset" -> tag;
			default -> color(tag) != null ? "color" : tag;
		};
	}

	private static boolean on(boolean off, String[] parts) {
		return !off && !(parts.length > 1 && parts[1].equals("false"));
	}

	private static TextColor color(String name) {
		if (name.startsWith("#") && name.length() != 7) return null;
		return TextColor.parseColor(name.replace("grey", "gray")).result().orElse(null);
	}

	/** Closes the tag {@code k} and those opened inside it; a gradient then knows its length and colours its letters. */
	private void close(int k) {
		while (open.size() > k) {
			Open tag = open.remove(open.size() - 1);
			cur = tag.saved;
			if (tag.stops == null) continue;
			int length = chars.size() - tag.start;
			for (int n = 0; n < length; n++) {
				Style style = styles.get(tag.start + n);
				if (style.getColor() != null) continue; // a colour given inside the gradient wins
				int rgb = tag.stops.isEmpty()
					? Mth.hsvToRgb((float) n / length, 1.0F, 1.0F)
					: gradient(tag.stops, length == 1 ? 0.0F : (float) n / (length - 1));
				styles.set(tag.start + n, style.withColor(rgb));
			}
		}
	}

	/** The colour at {@code t} (0 to 1) of a gradient going through every stop, each channel in a straight line. */
	static int gradient(List<TextColor> stops, float t) {
		if (stops.size() == 1) return stops.get(0).getValue();
		float at = t * (stops.size() - 1);
		int from = Math.min((int) at, stops.size() - 2);
		int a = stops.get(from).getValue();
		int b = stops.get(from + 1).getValue();
		int rgb = 0;
		for (int shift = 16; shift >= 0; shift -= 8) {
			rgb |= Math.round(Mth.lerp(at - from, a >> shift & 0xFF, b >> shift & 0xFF)) << shift;
		}
		return rgb;
	}

	private void add(int codePoint) {
		chars.add(codePoint);
		styles.add(source != null && cur == BASE && open.isEmpty() ? source[at] : cur);
	}

	/** One piece of text per run of letters of the same style. */
	static Component build(List<Integer> chars, List<Style> styles) {
		List<MutableComponent> runs = new ArrayList<>();
		StringBuilder run = new StringBuilder();
		for (int n = 0; n < chars.size(); n++) {
			run.appendCodePoint(chars.get(n));
			if (n + 1 == chars.size() || !styles.get(n).equals(styles.get(n + 1))) {
				runs.add(Component.literal(run.toString()).setStyle(styles.get(n)));
				run.setLength(0);
			}
		}
		if (runs.size() == 1) return runs.get(0);
		MutableComponent text = Component.empty();
		runs.forEach(text::append);
		return text;
	}

	private static boolean isHex(String s, int from, int count) {
		if (from + count > s.length()) return false;
		for (int k = from; k < from + count; k++) {
			if (Character.digit(s.charAt(k), 16) < 0) return false;
		}
		return true;
	}
}
