package com.imnot.nbteditorgradients;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The gradients the player saved under a name, in config/nbteditorgradients-presets.json; they go from one instance of the game to another as files. */
public final class Presets {
	/** One saved gradient, as it is written in the file: the colours as #rrggbb, their places from 0 to 1. */
	public static final class Preset {
		public String name;
		public String type;
		public String[] colors;
		public float[] positions;

		/** Puts the colours of the preset in a gradient; its other options stay. */
		public void load(Gradient gradient) {
			gradient.stops.clear();
			for (int n = 0; n < colors.length; n++) {
				gradient.stops.add(new Gradient.Stop(Integer.parseInt(colors[n].substring(1), 16), positions[n]));
			}
			gradient.type = Gradient.Type.valueOf(type);
			gradient.sort();
		}
	}

	private static final Path FILE = Config.file("presets.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static List<Preset> presets;

	private Presets() {}

	public static List<Preset> all() {
		if (presets == null) {
			presets = new ArrayList<>();
			try {
				for (Preset preset : GSON.fromJson(Files.readString(FILE), Preset[].class)) {
					// a file edited by hand: what cannot be a gradient is left out
					if (preset != null && preset.name != null && valid(preset)) presets.add(preset);
				}
			} catch (IOException | JsonParseException | NullPointerException noFile) {
				// first launch: three to start from, as on Birdflop
				presets.add(of("Birdflop", Gradient.Type.RGB, 0x54DAF4, 0x545EB6));
				presets.add(of("Rainbow", Gradient.Type.HSL, 0xFF5555, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF));
				presets.add(of("Fire", Gradient.Type.RGB, 0xFFE259, 0xFFA751, 0xFF5555));
			}
		}
		return presets;
	}

	private static boolean valid(Preset preset) {
		try {
			preset.load(new Gradient());
			return preset.colors.length > 0;
		} catch (RuntimeException broken) {
			return false;
		}
	}

	private static Preset of(String name, Gradient.Type type, int... colors) {
		Gradient gradient = new Gradient();
		gradient.stops.clear();
		for (int color : colors) gradient.stops.add(new Gradient.Stop(color, 0));
		gradient.disperse();
		gradient.type = type;
		return of(name, gradient);
	}

	private static Preset of(String name, Gradient gradient) {
		Preset preset = new Preset();
		preset.name = name;
		preset.type = gradient.type.name();
		preset.colors = gradient.stops.stream().map(stop -> String.format("#%06X", stop.rgb)).toArray(String[]::new);
		preset.positions = new float[gradient.stops.size()];
		for (int n = 0; n < preset.positions.length; n++) preset.positions[n] = gradient.stops.get(n).pos;
		return preset;
	}

	public static Preset find(String name) {
		for (Preset preset : all()) {
			if (preset.name.equals(name)) return preset;
		}
		return null;
	}

	/** Saves the gradient under this name: a new preset, or the new colours of the one that has the name. */
	public static void save(String name, Gradient gradient) {
		Preset old = find(name);
		Preset preset = of(name, gradient);
		if (old == null) all().add(preset);
		else all().set(all().indexOf(old), preset);
		write();
	}

	/** Gives a preset another name; a preset that already had that name gives way. */
	public static void rename(String name, String to) {
		Preset preset = find(name);
		if (!to.equals(name)) all().remove(find(to));
		preset.name = to;
		write();
	}

	public static void delete(String name) {
		all().remove(find(name));
		write();
	}

	/** The file a preset is exported to: its name in the list, less what the name of a file cannot hold. */
	public static String fileName(Preset preset) {
		return preset.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") + ".json";
	}

	/** Writes each preset in the folder, in a file of its own; returns how many were written. */
	public static int export(List<Preset> which, String folder) {
		int written = 0;
		for (Preset preset : which) {
			try {
				Files.writeString(Path.of(folder).resolve(fileName(preset)), GSON.toJson(preset));
				written++;
			} catch (IOException | InvalidPathException e) {
				// this one is not counted
			}
		}
		return written;
	}

	/**
	 * Adds the presets of files written by {@link #export}, or of the presets file of another instance. One that
	 * has the name of a preset of the list takes its place. Returns how many were read.
	 */
	public static int load(String... files) {
		int read = 0;
		for (String file : files) {
			try {
				JsonElement json = GSON.fromJson(Files.readString(Path.of(file)), JsonElement.class);
				for (Preset preset : json.isJsonArray() ? GSON.fromJson(json, Preset[].class) : new Preset[] {GSON.fromJson(json, Preset.class)}) {
					if (preset == null || !valid(preset)) continue;
					// a file written by hand may leave the name out: the name of the file then
					if (preset.name == null || preset.name.isBlank()) preset.name = Path.of(file).getFileName().toString().replaceFirst("\\.json$", "");
					all().remove(find(preset.name));
					all().add(preset);
					read++;
				}
			} catch (IOException | RuntimeException notPresets) {
				// a file that is something else adds nothing
			}
		}
		if (read > 0) write();
		return read;
	}

	private static void write() {
		try {
			Files.writeString(FILE, GSON.toJson(presets));
		} catch (IOException e) {
			// the presets still hold until the game closes
		}
	}
}
