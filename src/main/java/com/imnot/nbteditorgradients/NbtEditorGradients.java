package com.imnot.nbteditorgradients;

import com.imnot.nbteditorgradients.mixin.DisplayScreenAccessor;
import com.imnot.nbteditorgradients.mixin.LocalEditorScreenAccessor;
import com.luneruniverse.minecraft.mod.nbteditor.addons.NBTEditorAPI;
import com.luneruniverse.minecraft.mod.nbteditor.addons.NBTEditorAddon;
import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalItem;
import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalItemParts;
import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalItemStack;
import com.luneruniverse.minecraft.mod.nbteditor.nbtreferences.itemreferences.ItemReference;
import com.luneruniverse.minecraft.mod.nbteditor.screens.configurable.ConfigItem;
import com.luneruniverse.minecraft.mod.nbteditor.screens.configurable.ConfigValueNumber;
import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * /iname and /ilore write a colour code of any format on the held item; Ctrl+Z and Ctrl+Y in the display
 * screen of NBT Editor go back and forth through what was done to the item.
 */
public class NbtEditorGradients implements ClientModInitializer, NBTEditorAddon {
	private static final SimpleCommandExceptionType INVALID_LINE = new SimpleCommandExceptionType(Component.translatable("nbteditor.lore.invalid_line"));

	private interface LoreEdit {
		void apply(List<Component> lines) throws CommandSyntaxException;
	}

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> {
			dispatcher.register(literal("iname")
				.then(argument("texte", StringArgumentType.greedyString()).executes(ctx -> {
					ItemReference ref = ItemReference.getHeldItem();
					ItemStack before = ref.getItem().copy();
					ItemStack item = before.copy();
					Component name = text(ctx);
					item.set(DataComponents.CUSTOM_NAME, name);
					return save(ref, before, item, Component.translatable("nbteditor.named").append(name));
				})));
			dispatcher.register(literal("ilore")
				.then(literal("add")
					.then(argument("texte", StringArgumentType.greedyString()).executes(ctx -> lore(lines -> lines.add(text(ctx))))))
				.then(literal("set")
					.then(argument("ligne", IntegerArgumentType.integer(1)).suggests((ctx, builder) -> suggestLines(builder))
						.then(argument("texte", StringArgumentType.greedyString())
							.executes(ctx -> lore(lines -> lines.set(line(ctx, lines), text(ctx)))))))
				.then(literal("remove")
					.then(argument("ligne", IntegerArgumentType.integer(1)).suggests((ctx, builder) -> suggestLines(builder))
						.executes(ctx -> lore(lines -> lines.remove(line(ctx, lines))))
						// the text of the line, to see what is about to be removed; what is typed there changes nothing
						.then(argument("apercu", StringArgumentType.greedyString())
							.suggests((ctx, builder) -> {
								List<Component> lines = heldLore();
								int line = IntegerArgumentType.getInteger(ctx, "ligne");
								if (line <= lines.size()) builder.suggest(lines.get(line - 1).getString());
								return builder.buildFuture();
							})
							.executes(ctx -> lore(lines -> lines.remove(line(ctx, lines))))))));
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> History.clear());
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof DisplayScreen<?> display)) return;
			ScreenEvents.afterTick(screen).register(s -> DisplayAddon.tick(display));
			ScreenMouseEvents.beforeMouseClick(screen).register((s, click) -> DisplayAddon.click(display, click.x(), click.y()));
			if (!(((LocalEditorScreenAccessor) screen).getLocalNBT() instanceof LocalItem item)) return;
			History.open(item.getReadableItem());
			ScreenEvents.afterTick(screen).register(NbtEditorGradients::recordTyping);
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> !undoKey((DisplayScreen<?>) s, key));
		});
	}

	// Brigadier's own builders: the helper class of Fabric changed its name between two versions of Minecraft
	private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
		return LiteralArgumentBuilder.literal(name);
	}

	private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
		return RequiredArgumentBuilder.argument(name, type);
	}

	/** Called by NBT Editor once its own options exist. */
	@Override
	public void onInit() {
		NBTEditorAPI.registerConfigItem("undoLimit", () -> new ConfigItem<>(
			Component.translatable("nbteditorgradients.config.undo_limit"),
			ConfigValueNumber.forInt(Config.undoLimit(), Config.DEFAULT_UNDO_LIMIT, 1, 1000)
				.addValueListener(value -> Config.setUndoLimit(value.getValidValue())))
			.setTooltip("nbteditorgradients.config.undo_limit.desc"));
	}

	private static Component text(CommandContext<FabricClientCommandSource> ctx) {
		return ColorText.parse(StringArgumentType.getString(ctx, "texte"), ctx.getSource().getPlayer().registryAccess());
	}

	/** The index of the line given in the command, where the first line is 1. */
	private static int line(CommandContext<FabricClientCommandSource> ctx, List<Component> lines) throws CommandSyntaxException {
		int line = IntegerArgumentType.getInteger(ctx, "ligne");
		if (line > lines.size()) throw INVALID_LINE.create();
		return line - 1;
	}

	private static int lore(LoreEdit edit) throws CommandSyntaxException {
		ItemReference ref = ItemReference.getHeldItem();
		ItemStack before = ref.getItem().copy();
		ItemStack item = before.copy();
		List<Component> lines = new ArrayList<>(item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
		edit.apply(lines);
		item.set(DataComponents.LORE, new ItemLore(lines));
		return save(ref, before, item, Component.translatable("nbteditor.lore.edited"));
	}

	private static int save(ItemReference ref, ItemStack before, ItemStack item, Component message) {
		History.open(before);
		History.record(item, false);
		ref.saveItem(item, message);
		return 1;
	}

	private static List<Component> heldLore() {
		LocalPlayer player = Minecraft.getInstance().player;
		ItemStack held = player.getMainHandItem().isEmpty() ? player.getOffhandItem() : player.getMainHandItem();
		return held.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	private static CompletableFuture<Suggestions> suggestLines(
		SuggestionsBuilder builder) {
		List<Component> lines = heldLore();
		for (int line = 0; line < lines.size(); line++) builder.suggest(line + 1, lines.get(line));
		return builder.buildFuture();
	}

	private static void recordTyping(Screen screen) {
		if (((LocalEditorScreenAccessor) screen).getLocalNBT() instanceof LocalItem item) History.record(item.getReadableItem(), true);
	}

	/** Ctrl+Z / Ctrl+Y in the display screen; true when the key was one of them. */
	private static boolean undoKey(DisplayScreen<?> screen, KeyEvent key) {
		// Ctrl+Alt is AltGr; the key is the one that types z or y on the keyboard of the player, wherever it is
		Minecraft mc = Minecraft.getInstance();
		if (!mc.hasControlDown() || mc.hasShiftDown() || mc.hasAltDown() || screen.getOverlay() != null) return false;
		String typed = InputConstants.getKey(key).getDisplayName().getString();
		int direction = "z".equalsIgnoreCase(typed) ? -1 : "y".equalsIgnoreCase(typed) ? 1 : 0;
		LocalEditorScreenAccessor editor = (LocalEditorScreenAccessor) screen;
		if (direction == 0 || !(editor.getLocalNBT() instanceof LocalItem item)) return false;
		recordTyping(screen);
		ItemStack state = History.step(direction);
		if (state != null) {
			LocalItemStack restored = new LocalItemStack(state);
			editor.setLocalNBT(item instanceof LocalItemParts ? restored.toParts() : restored);
			// the screen keeps the text of its fields from one init to the next: without them it reads the item again
			((DisplayScreenAccessor) screen).setNameFormatted(null);
			((DisplayScreenAccessor) screen).setLore(null);
			screen.resize(screen.width, screen.height);
			editor.invokeCheckSave();
		}
		return true;
	}
}
