package com.imnot.nbteditorgradients;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** A gradient as the editor builds it: colour stops on a line, and the options of Birdflop's generator that NBT Editor does not have. */
public final class Gradient {
	/** The space the colours between two stops are taken from. */
	public enum Type { RGB, HSL, OKLAB, OKLCH }

	/** A colour at a place of the gradient, from 0 (first letter) to 1 (last letter). */
	public static final class Stop {
		public int rgb;
		public float pos;

		public Stop(int rgb, float pos) {
			this.rgb = rgb;
			this.pos = pos;
		}
	}

	/** Kept in the order of their place. */
	public final List<Stop> stops = new ArrayList<>();
	public Type type = Type.RGB;
	/** How many letters in a row share a colour. */
	public int colorLength = 1;
	/** Spaces keep their style and do not use up a colour. */
	public boolean skipSpaces = true;
	public boolean lowercase;
	/** Each word goes from the first stop to the last, instead of each line. */
	public boolean perWord;

	/** The gradient Birdflop opens with. */
	public Gradient() {
		stops.add(new Stop(0x54DAF4, 0.0F));
		stops.add(new Stop(0x545EB6, 1.0F));
	}

	public void sort() {
		stops.sort(Comparator.comparingDouble(stop -> stop.pos));
	}

	/** Puts the stops at equal distances, in their order. */
	public void disperse() {
		for (int n = 0; n < stops.size(); n++) stops.get(n).pos = stops.size() == 1 ? 0.0F : (float) n / (stops.size() - 1);
	}

	/** A number that changes when anything that changes the colours does: what is drawn from the gradient is kept until then. */
	public int state() {
		int state = type.ordinal() + 31 * colorLength + (skipSpaces ? 961 : 0) + (lowercase ? 29791 : 0) + (perWord ? 923521 : 0);
		for (Stop stop : stops) state = 31 * (31 * state + stop.rgb) + Float.floatToIntBits(stop.pos);
		return state;
	}

	/** The colour at {@code t}, from 0 to 1. To ask for many colours, take a {@link #sampler()} once. */
	public int at(float t) {
		return sampler().at(t);
	}

	/** The gradient as it is now, with the stops already taken to the colour space: a colour then costs one blend. */
	public Sampler sampler() {
		return new Sampler(this);
	}

	public static final class Sampler {
		private final Type type;
		private final float[] pos;
		private final int[] rgb;
		/** For each pair of stops that follow each other, the two ends of the blend. */
		private final double[][][] ends;

		private Sampler(Gradient gradient) {
			type = gradient.type;
			int count = gradient.stops.size();
			pos = new float[count];
			rgb = new int[count];
			for (int n = 0; n < count; n++) {
				pos[n] = gradient.stops.get(n).pos;
				rgb[n] = gradient.stops.get(n).rgb;
			}
			ends = new double[Math.max(0, count - 1)][][];
			for (int n = 0; n + 1 < count; n++) ends[n] = ends(type, rgb[n], rgb[n + 1]);
		}

		public int at(float t) {
			if (t <= pos[0]) return rgb[0];
			for (int n = 1; n < pos.length; n++) {
				if (t <= pos[n]) return pos[n] == pos[n - 1] ? rgb[n] : blend(type, ends[n - 1], (t - pos[n - 1]) / (pos[n] - pos[n - 1]));
			}
			return rgb[rgb.length - 1];
		}
	}

	/**
	 * Colours the letters of {@code text} that are in one of the {@code ranges} (start and end, in chars); what the
	 * letters had besides a colour (bold...) stays. The gradient restarts at each line, or at each word.
	 */
	public Component apply(Component text, List<int[]> ranges) {
		List<Integer> chars = new ArrayList<>();
		List<Style> styles = new ArrayList<>();
		List<Boolean> chosen = new ArrayList<>();
		int[] at = {0};
		text.visit((style, part) -> {
			part.codePoints().forEach(c -> {
				boolean in = false;
				for (int[] range : ranges) in |= at[0] >= range[0] && at[0] < range[1];
				chars.add(in && lowercase ? Character.toLowerCase(c) : c);
				styles.add(style);
				chosen.add(in);
				at[0] += Character.charCount(c);
			});
			return Optional.empty();
		}, Style.EMPTY);

		Sampler sampler = sampler();
		List<Integer> letters = new ArrayList<>();
		for (int n = 0; n <= chars.size(); n++) {
			boolean end = n == chars.size();
			boolean space = !end && Character.isWhitespace(chars.get(n));
			if (end || chars.get(n) == '\n' || perWord && (space || !chosen.get(n))) {
				color(sampler, styles, letters);
				letters.clear();
			} else if (chosen.get(n) && !(space && skipSpaces)) {
				letters.add(n);
			}
		}
		return ColorText.build(chars, styles);
	}

	private void color(Sampler sampler, List<Style> styles, List<Integer> letters) {
		int colors = (letters.size() + colorLength - 1) / colorLength;
		for (int k = 0; k < letters.size(); k++) {
			float t = colors <= 1 ? 0.0F : (float) (k / colorLength) / (colors - 1);
			styles.set(letters.get(k), styles.get(letters.get(k)).withColor(sampler.at(t)));
		}
	}

	/**
	 * Makes the gradient that gives these colours to as many letters: the two ends, then a stop wherever the
	 * colours leave the line between two stops.
	 */
	public void fit(List<Integer> colors) {
		stops.clear();
		int last = colors.size() - 1;
		stops.add(new Stop(colors.get(0), 0.0F));
		if (last > 0) stops.add(new Stop(colors.get(last), 1.0F));
		// ponytail: one pass over the letters per stop added, at most 12 stops; enough for a text typed by hand
		while (stops.size() < 12) {
			Sampler sampler = sampler();
			int worst = -1, error = 8;
			for (int n = 1; n < last; n++) {
				int off = distance(sampler.at((float) n / last), colors.get(n));
				if (off > error) {
					error = off;
					worst = n;
				}
			}
			if (worst < 0) break;
			stops.add(new Stop(colors.get(worst), (float) worst / last));
			sort();
		}
	}

	private static int distance(int a, int b) {
		return Math.max(Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)), Math.max(Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)), Math.abs((a & 0xFF) - (b & 0xFF))));
	}

	/** The colour at {@code f} (0 to 1) between two colours, in a straight line through the space of {@code type}. */
	public static int mix(Type type, int a, int b, float f) {
		return blend(type, ends(type, a, b), f);
	}

	/** Two colours in the space of {@code type}, ready to be blended. */
	private static double[][] ends(Type type, int a, int b) {
		double[] from = toSpace(type, a);
		double[] to = toSpace(type, b);
		int hue = type == Type.HSL ? 0 : type == Type.OKLCH ? 2 : -1;
		if (hue >= 0) {
			// a grey has no hue of its own: it takes the one of the other colour instead of going round the wheel
			int chroma = 1;
			if (from[chroma] < 1e-4) from[hue] = to[hue];
			if (to[chroma] < 1e-4) to[hue] = from[hue];
			// the short way round
			if (to[hue] - from[hue] > 0.5) from[hue] += 1;
			if (from[hue] - to[hue] > 0.5) to[hue] += 1;
		}
		return new double[][] {from, to};
	}

	private static int blend(Type type, double[][] ends, float f) {
		double[] at = new double[3];
		for (int n = 0; n < 3; n++) at[n] = ends[0][n] + (ends[1][n] - ends[0][n]) * f;
		return fromSpace(type, at);
	}

	private static double[] toSpace(Type type, int rgb) {
		double r = (rgb >> 16 & 0xFF) / 255.0;
		double g = (rgb >> 8 & 0xFF) / 255.0;
		double b = (rgb & 0xFF) / 255.0;
		switch (type) {
			case RGB:
				return new double[] {r, g, b};
			case HSL: {
				double max = Math.max(r, Math.max(g, b));
				double min = Math.min(r, Math.min(g, b));
				double l = (max + min) / 2;
				double d = max - min;
				if (d == 0) return new double[] {0, 0, l};
				double s = d / (1 - Math.abs(2 * l - 1));
				double h = max == r ? (g - b) / d % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
				return new double[] {(h / 6 + 1) % 1, s, l};
			}
			default: {
				double lr = linear(r);
				double lg = linear(g);
				double lb = linear(b);
				double l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
				double m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
				double s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
				double okL = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
				double okA = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
				double okB = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
				if (type == Type.OKLAB) return new double[] {okL, okA, okB};
				return new double[] {okL, Math.hypot(okA, okB), (Math.atan2(okB, okA) / (2 * Math.PI) + 1) % 1};
			}
		}
	}

	private static int fromSpace(Type type, double[] v) {
		switch (type) {
			case RGB:
				return pack(v[0], v[1], v[2]);
			case HSL: {
				double h = (v[0] % 1 + 1) % 1 * 6;
				double c = (1 - Math.abs(2 * v[2] - 1)) * v[1];
				double x = c * (1 - Math.abs(h % 2 - 1));
				double m = v[2] - c / 2;
				double[] rgb = h < 1 ? new double[] {c, x, 0} : h < 2 ? new double[] {x, c, 0} : h < 3 ? new double[] {0, c, x}
					: h < 4 ? new double[] {0, x, c} : h < 5 ? new double[] {x, 0, c} : new double[] {c, 0, x};
				return pack(rgb[0] + m, rgb[1] + m, rgb[2] + m);
			}
			default: {
				double okA = type == Type.OKLAB ? v[1] : v[1] * Math.cos(v[2] * 2 * Math.PI);
				double okB = type == Type.OKLAB ? v[2] : v[1] * Math.sin(v[2] * 2 * Math.PI);
				double l = Math.pow(v[0] + 0.3963377774 * okA + 0.2158037573 * okB, 3);
				double m = Math.pow(v[0] - 0.1055613458 * okA - 0.0638541728 * okB, 3);
				double s = Math.pow(v[0] - 0.0894841775 * okA - 1.2914855480 * okB, 3);
				return pack(
					gamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
					gamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
					gamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s));
			}
		}
	}

	private static double linear(double c) {
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static double gamma(double c) {
		return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
	}

	private static int pack(double r, double g, double b) {
		return channel(r) << 16 | channel(g) << 8 | channel(b);
	}

	private static int channel(double c) {
		return (int) Math.round(Math.max(0, Math.min(1, c)) * 255);
	}
}
