package com.imnot.nbteditorgradients;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The one option of the mod, shown in the options of NBT Editor: a file that holds a number. */
final class Config {
	static final int DEFAULT_UNDO_LIMIT = 50;
	private static final Path FILE = file("undo-limit.txt");
	private static int undoLimit = DEFAULT_UNDO_LIMIT;

	static {
		try {
			undoLimit = Math.max(1, Integer.parseInt(Files.readString(FILE).trim()));
		} catch (IOException | NumberFormatException noFile) {
			// first launch, or a file edited by hand: the default
		}
	}

	private Config() {}

	/** A file of the mod in the config folder. The one written under the former name of the mod is taken over. */
	static Path file(String name) {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path file = dir.resolve("nbteditorgradients-" + name);
		Path former = dir.resolve("nbteditorcolors-" + name);
		try {
			if (Files.notExists(file) && Files.exists(former)) Files.move(former, file);
		} catch (IOException e) {
			// the file starts again from nothing
		}
		return file;
	}

	/** How many steps Ctrl+Z can go back. */
	static int undoLimit() {
		return undoLimit;
	}

	static void setUndoLimit(int limit) {
		undoLimit = limit;
		try {
			Files.writeString(FILE, Integer.toString(limit));
		} catch (IOException e) {
			// the limit still holds until the game closes
		}
	}
}
