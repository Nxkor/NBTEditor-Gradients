package com.imnot.nbteditorgradients;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The file windows of the system. They are waited for away from the game, which goes on; the answer comes back
 * on the thread of the game, and not at all when the player gave up.
 */
final class FileDialogs {
	private FileDialogs() {}

	static void folder(String title, Consumer<String> chosen) {
		CompletableFuture.supplyAsync(() -> TinyFileDialogs.tinyfd_selectFolderDialog(title, "")).thenAcceptAsync(folder -> {
			if (folder != null) chosen.accept(folder);
		}, Minecraft.getInstance());
	}

	/** One file or several. */
	static void files(String title, Consumer<String[]> chosen) {
		CompletableFuture.supplyAsync(() -> TinyFileDialogs.tinyfd_openFileDialog(title, "", null, null, true)).thenAcceptAsync(files -> {
			if (files != null) chosen.accept(files.split("\\|"));
		}, Minecraft.getInstance());
	}
}
