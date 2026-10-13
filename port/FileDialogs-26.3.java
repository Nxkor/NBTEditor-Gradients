package com.imnot.nbteditorgradients;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;

/**
 * The file windows of the system. 26.3 dropped tinyfd for SDL: the game goes on while they are open, and the
 * answer comes through a callback, possibly on another thread. It is handed to the thread of the game, and not at
 * all when the player gave up. (SDL gives its windows no title.)
 */
final class FileDialogs {
	private FileDialogs() {}

	static void folder(String title, Consumer<String> chosen) {
		ask(true, paths -> chosen.accept(paths[0]));
	}

	/** One file or several. */
	static void files(String title, Consumer<String[]> chosen) {
		ask(false, chosen);
	}

	private static void ask(boolean folder, Consumer<String[]> chosen) {
		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, files, filter) -> {
			// a list of paths that ends with a null one; no list is an error, an empty list the player giving up
			List<String> paths = new ArrayList<>();
			for (long at = files; at != 0 && MemoryUtil.memGetAddress(at) != 0; at += Pointer.POINTER_SIZE) paths.add(MemoryUtil.memUTF8(MemoryUtil.memGetAddress(at)));
			Minecraft.getInstance().execute(() -> {
				callback[0].free();
				if (!paths.isEmpty()) chosen.accept(paths.toArray(new String[0]));
			});
		});
		long window = Minecraft.getInstance().getWindow().handle();
		String start = System.getProperty("user.home");
		if (folder) SDLDialog.SDL_ShowOpenFolderDialog(callback[0], 0, window, start, false);
		else SDLDialog.SDL_ShowOpenFileDialog(callback[0], 0, window, null, start, true);
	}
}
