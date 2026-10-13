package com.imnot.nbteditorgradients.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.imnot.nbteditorgradients.ColorText;
import com.imnot.nbteditorgradients.Gradient;
import com.imnot.nbteditorgradients.GradientPanel;
import com.imnot.nbteditorgradients.Presets;
import com.imnot.nbteditorgradients.mixin.DisplayScreenAccessor;
import com.imnot.nbteditorgradients.mixin.LocalEditorScreenAccessor;
import com.luneruniverse.minecraft.mod.nbteditor.commands.get.GetLostItemCommand;
import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalItem;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVMisc;
import com.luneruniverse.minecraft.mod.nbteditor.screens.OverlaySupportingScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.MultiLineTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.util.SingleDynamicItem;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.input.KeyEvent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Reads the codes that Birdflop's own generator wrote in each of its formats (birdflop.json, made by
 * port/birdflop.mjs), then names and describes a sword of a real creative world with /iname and /ilore and goes
 * back and forth in the display screen. Screenshots land in build/run/clientGameTest/screenshots.
 */
public class NbtEditorGradientsClientTest implements FabricClientGameTest {
	/** One visible letter as Minecraft draws it in a lore, where nothing said means italic. */
	private record Letter(int letter, Integer rgb, boolean bold, boolean italic) {}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitTicks(40); // let the world load
			TestServerContext server = world.getServer();
			JsonObject birdflop = birdflop();

			// --- the seven formats of Birdflop give the same letters ---
			List<Letter> plain = letters(ctx, birdflop.get("plain_2").getAsString());
			List<Letter> strong = letters(ctx, birdflop.get("bolditalic_2").getAsString());
			check(plain.size() == 12 && plain.get(0).equals(new Letter('E', 0xFF5555, false, false)) && plain.get(11).equals(new Letter('n', 0x55FFFF, false, false)),
				"the JSON of Birdflop is read, without italic: " + plain);
			check(strong.stream().allMatch(l -> l.bold && l.italic), "bold and italic asked in JSON are kept: " + strong);
			for (int format = 1; format <= 6; format++) {
				List<Letter> read = letters(ctx, birdflop.get("plain_" + format).getAsString());
				check(read.equals(plain), "format " + format + " gives the colours of the JSON: " + read);
				read = letters(ctx, birdflop.get("bolditalic_" + format).getAsString());
				check(read.equals(strong), "format " + format + " in bold italic gives the letters of the JSON: " + read);
			}
			List<Letter> mini = letters(ctx, birdflop.get("plain_0").getAsString());
			check(mini.size() == 12 && mini.get(0).equals(plain.get(0)) && mini.get(11).equals(plain.get(11)) && mini.stream().noneMatch(l -> l.rgb == null),
				"a MiniMessage gradient goes from the first colour to the last: " + mini);
			check(letters(ctx, birdflop.get("bolditalic_0").getAsString()).stream().allMatch(l -> l.bold && l.italic && l.rgb != null),
				"a MiniMessage gradient in bold italic");

			// --- by hand ---
			check(letters(ctx, "<gradient:#000000:#ffffff>abc</gradient>d").equals(List.of(
				new Letter('a', 0x000000, false, false), new Letter('b', 0x808080, false, false), new Letter('c', 0xFFFFFF, false, false), new Letter('d', null, false, false))),
				"the middle of a gradient is the middle colour, and the gradient ends at its closing tag");
			check(letters(ctx, "#54DAF4Bi&lr&cd").equals(List.of(
				new Letter('B', 0x54DAF4, false, false), new Letter('i', 0x54DAF4, false, false), new Letter('r', 0x54DAF4, true, false), new Letter('d', 0xFF5555, false, false))),
				"#rrggbb without & is a colour, &l is bold until the next colour");
			check(text(ctx, "a < b > c [x] <3 & d").equals("a < b > c [x] <3 & d"), "what is not a code stays as written");
			check(letters(ctx, "<rainbow>abcdef").stream().map(Letter::rgb).distinct().count() == 6, "a rainbow gives one colour per letter");

			// --- on a real item ---
			server.runCommand("gamemode creative @a");
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
			ctx.waitTicks(10);
			command(ctx, "iname " + birdflop.get("plain_0").getAsString());
			command(ctx, "ilore add " + birdflop.get("default").getAsString());
			command(ctx, "ilore add [COLOR=#FF5555]a[/COLOR] supprimer");
			command(ctx, "ilore add <gradient:#ff5555:#55ffff><b>Tranchant</b> XV</gradient>");
			command(ctx, "ilore set 1 " + birdflop.get("bolditalic_1").getAsString());
			command(ctx, "ilore remove 2 a supprimer");
			ItemStack sword = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getMainHandItem().copy());
			List<Component> lore = sword.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
			check(sword.getHoverName().getString().equals("Epee du Dragon") && letters(sword.getHoverName()).equals(mini),
				"/iname names the item on the server: " + sword.getHoverName() + ", on the client: " + ctx.computeOnClient(mc -> mc.player.getMainHandItem().getHoverName()));
			check(lore.size() == 2 && letters(lore.get(0)).equals(strong) && lore.get(1).getString().equals("Tranchant XV"),
				"/ilore add, set and remove leave two lines: " + lore);

			command(ctx, "ilore remove 5");
			check(lore(ctx).size() == 2, "a line that does not exist removes nothing");

			// --- an item that is edited goes to the history of /get lostitem, as it was and as it became ---
			List<ItemStack> lost = ctx.computeOnClient(mc -> lostItems());
			check(lost.size() == 7 && ItemStack.matches(lost.get(0), sword) && !lost.get(6).has(DataComponents.CUSTOM_NAME) && lost.get(5).has(DataComponents.CUSTOM_NAME),
				"the sword before the commands and after each of the six that changed it are in the history of /get lostitem: " + lost);

			// --- Ctrl+Z / Ctrl+Y in the display screen: the steps of the commands are still there ---
			ctx.runOnClient(mc -> mc.player.connection.sendCommand("factory display"));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_display");
			check(ctx.computeOnClient(mc -> mc.screen instanceof DisplayScreen<?>), "the display screen is open");
			int z = key(ctx, "z");
			int y = key(ctx, "y");
			ctx.getInput().holdKey(InputConstants.KEY_LCONTROL);
			ctx.getInput().pressKey(z);
			ctx.waitTicks(3);
			check(screenLore(ctx).size() == 3, "Ctrl+Z brings back the removed line: " + screenLore(ctx));
			ctx.getInput().pressKey(z);
			ctx.waitTicks(3);
			check(letters(screenLore(ctx).get(0)).size() == 8, "a second Ctrl+Z brings back the first line as it was (Birdflop): " + screenLore(ctx));
			ctx.takeScreenshot("02_undone_twice");
			for (int n = 0; n < 10; n++) ctx.getInput().pressKey(z);
			ctx.waitTicks(3);
			check(screenLore(ctx).isEmpty() && !screenItem(ctx).has(DataComponents.CUSTOM_NAME), "Ctrl+Z as far as it goes: the sword before the commands");
			for (int n = 0; n < 10; n++) ctx.getInput().pressKey(y);
			ctx.waitTicks(3);
			ctx.getInput().releaseKey(InputConstants.KEY_LCONTROL);
			check(ItemStack.matches(screenItem(ctx), sword), "Ctrl+Y as far as it goes: the sword as the commands left it");
			ctx.takeScreenshot("03_redone");

			// --- codes typed in the lore field, then the convert button of that field ---
			ctx.runOnClient(mc -> fields(mc).getLore().setText(List.of(Component.literal("&#FF0000Rouge <b>gras</b>"), Component.literal("deux").withStyle(s -> s.withColor(0x00FF00)))));
			ctx.waitTicks(2);
			ctx.takeScreenshot("04_codes_typed");
			// the convert button has an icon and no label, as the reset button before it: it is the last of the two
			ctx.runOnClient(mc -> MVMisc.triggerButtonPress(fields(mc).getLore().children().stream()
				.filter(child -> child instanceof Button button && button.getMessage().getString().isEmpty())
				.map(Button.class::cast).reduce((a, b) -> b).orElseThrow()));
			ctx.waitTicks(2);
			List<Component> converted = screenLore(ctx);
			check(converted.get(0).getString().equals("Rouge gras") && letters(converted.get(0)).get(0).equals(new Letter('R', 0xFF0000, false, false))
				&& letters(converted.get(0)).get(5).equals(new Letter('g', 0xFF0000, true, false)), "the convert button reads the codes of the field: " + converted);
			check(letters(converted.get(1)).get(0).rgb == 0x00FF00, "a line without code keeps its colour: " + converted);
			ctx.takeScreenshot("05_converted");

			// --- Home and End stop at the ends of the line the cursor is on; with Ctrl, at the ends of the text ---
			click(ctx, ctx.computeOnClient(mc -> new double[] {fields(mc).getLore().getX() + 12, fields(mc).getLore().getY() + 24 + 12}), InputConstants.MOUSE_BUTTON_LEFT);
			check(ctx.computeOnClient(mc -> loreField(mc).getText().equals("Rouge gras\ndeux")), "the lore field holds two lines");
			int[] ends = {cursorAfter(ctx, 13, InputConstants.KEY_HOME), cursorAfter(ctx, 13, InputConstants.KEY_END), cursorAfter(ctx, 4, InputConstants.KEY_HOME), cursorAfter(ctx, 4, InputConstants.KEY_END)};
			check(ends[0] == 11 && ends[1] == 15 && ends[2] == 0 && ends[3] == 10, "Home and End go to the ends of the line of the cursor: " + java.util.Arrays.toString(ends));
			ends = new int[] {cursorAfter(ctx, 13, InputConstants.KEY_UP), cursorAfter(ctx, 2, InputConstants.KEY_DOWN)};
			check(ends[0] == 2 && ends[1] == 13, "Up and Down keep the column between two lines that start as wide: " + java.util.Arrays.toString(ends));
			ctx.getInput().holdKey(InputConstants.KEY_LCONTROL);
			ends = new int[] {cursorAfter(ctx, 13, InputConstants.KEY_HOME), cursorAfter(ctx, 4, InputConstants.KEY_END)};
			ctx.getInput().releaseKey(InputConstants.KEY_LCONTROL);
			check(ends[0] == 0 && ends[1] == 15, "Ctrl+Home and Ctrl+End go to the ends of the text: " + java.util.Arrays.toString(ends));
			// back in the name field: the gradient editor works on the field the player was last in
			click(ctx, ctx.computeOnClient(mc -> new double[] {fields(mc).getNameFormatted().getX() + 12, fields(mc).getNameFormatted().getY() + 24 + 12}), InputConstants.MOUSE_BUTTON_LEFT);

			// --- the gradient editor: opened by its button, applied to the name ---
			String gradient = ctx.computeOnClient(mc -> Component.translatable("nbteditorgradients.gradient").getString());
			press(ctx, mc -> fields(mc).getLore(), gradient);
			ctx.waitTicks(2);
			check(panel(ctx) != null, "the gradient button opens the panel");
			press(ctx, mc -> panel(mc), "+");
			ctx.waitTicks(2);
			ctx.takeScreenshot("06_gradient_panel");
			press(ctx, mc -> panel(mc), ctx.computeOnClient(mc -> Component.translatable("nbteditorgradients.gradient.apply").getString()));
			ctx.waitTicks(2);
			List<Letter> named = letters(screenItem(ctx).getHoverName());
			check(named.size() == 12 && named.get(0).rgb == 0x54DAF4 && named.get(11).rgb == 0x545EB6, "Apply colours the name from the first stop to the last: " + named);
			ctx.takeScreenshot("07_gradient_applied");
			ctx.runOnClient(mc -> panel(mc).fold());
			ctx.waitTicks(2);
			ctx.takeScreenshot("08_gradient_folded");

			// --- a window twice as wide for the interface: the controls of the panel fit on one row ---
			ctx.runOnClient(mc -> {
				panel(mc).fold();
				mc.options.guiScale().set(1);
				mc.resizeDisplay();
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("09_wide");

			// --- a right click on an option goes back: one letter per colour, then ten, then one again ---
			click(ctx, button(ctx, tr(ctx, "nbteditorgradients.gradient.length", "1")), InputConstants.MOUSE_BUTTON_RIGHT);
			check(button(ctx, tr(ctx, "nbteditorgradients.gradient.length", "10")) != null, "a right click on the length goes to the one before");
			click(ctx, button(ctx, tr(ctx, "nbteditorgradients.gradient.length", "10")), InputConstants.MOUSE_BUTTON_LEFT);
			check(button(ctx, tr(ctx, "nbteditorgradients.gradient.length", "1")) != null, "a left click on the length goes to the next one");
			click(ctx, button(ctx, tr(ctx, "nbteditorgradients.gradient.type", "RGB")), InputConstants.MOUSE_BUTTON_RIGHT);
			check(button(ctx, tr(ctx, "nbteditorgradients.gradient.type", "OKLCH")) != null, "a right click on the blend goes to the one before");
			click(ctx, button(ctx, tr(ctx, "nbteditorgradients.gradient.type", "OKLCH")), InputConstants.MOUSE_BUTTON_LEFT);

			// --- a click on a marker opens its colour, a drag moves it ---
			double[] marker = ctx.computeOnClient(mc -> new double[] {25, panel(mc).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().getY() - 14});
			click(ctx, marker, InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			check(overlay(ctx), "a click on a marker opens the colour picker");
			ctx.takeScreenshot("10_picker");
			// a click in the dark of the square: the ring goes where the mouse is
			click(ctx, ctx.computeOnClient(mc -> new double[] {(mc.screen.width - 196) / 2 + 100, (mc.screen.height - 152 - 24) / 2 + 118}), InputConstants.MOUSE_BUTTON_LEFT);
			ctx.takeScreenshot("10_picker_dark");
			ctx.runOnClient(mc -> OverlaySupportingScreen.setOverlayStatic(null));
			ctx.getInput().setCursorPos(marker[0], marker[1]);
			ctx.waitTick();
			ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTick();
			ctx.getInput().setCursorPos(marker[0] + 60, marker[1]);
			ctx.waitTick();
			ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(3);
			check(!overlay(ctx), "a marker that is dragged does not open the colour picker");

			// --- a selection, then another one with Ctrl held: both count, letter by letter ---
			List<Letter> before = letters(screenItem(ctx).getHoverName());
			double[] name = ctx.computeOnClient(mc -> new double[] {fields(mc).getNameFormatted().getX() + 9, fields(mc).getNameFormatted().getY() + 24 + 12,
				mc.font.width("Epee"), mc.font.width("Epee du "), mc.font.width("Epee du Dragon")});
			drag(ctx, name[0], name[0] + name[2] + 3, name[1]);
			ctx.getInput().holdKey(InputConstants.KEY_LCONTROL);
			drag(ctx, name[0] + name[3] + 1, name[0] + name[4] + 3, name[1]);
			ctx.getInput().releaseKey(InputConstants.KEY_LCONTROL);
			ctx.waitTicks(3);
			ctx.takeScreenshot("11_selections");
			String apply = tr(ctx, "nbteditorgradients.gradient.apply");
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.scope", tr(ctx, "nbteditorgradients.gradient.scope.line")));
			press(ctx, mc -> panel(mc), apply);
			ctx.waitTicks(2);
			List<Letter> words = letters(screenItem(ctx).getHoverName());
			check(words.get(0).rgb.equals(words.get(6).rgb) && words.get(3).rgb.equals(words.get(11).rgb) && !words.get(0).rgb.equals(words.get(3).rgb)
				&& words.get(4).equals(before.get(4)) && words.get(5).equals(before.get(5)), "per word: each selected word has the whole gradient, the word between keeps its colours: " + words);
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.scope", tr(ctx, "nbteditorgradients.gradient.scope.word")));
			press(ctx, mc -> panel(mc), apply);
			ctx.waitTicks(2);
			List<Letter> unified = letters(screenItem(ctx).getHoverName());
			check(unified.get(0).rgb.equals(words.get(0).rgb) && unified.get(11).rgb.equals(words.get(11).rgb) && !unified.get(3).rgb.equals(unified.get(11).rgb)
				&& !unified.get(6).rgb.equals(unified.get(0).rgb) && unified.get(4).equals(before.get(4)), "unified: one gradient over the two selections: " + unified);
			ctx.takeScreenshot("12_selections_unified");

			// --- a preset chosen with nothing selected only changes the editor; the colours of the text then come back in it ---
			click(ctx, new double[] {name[0] + 2, name[1]}, InputConstants.MOUSE_BUTTON_LEFT); // an ordinary click: nothing selected any more
			ctx.waitTicks(2);
			choose(ctx, 1);
			check(letters(screenItem(ctx).getHoverName()).equals(unified), "a preset chosen with no selection leaves the text as it is");
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.pickup"));
			press(ctx, mc -> panel(mc), apply);
			ctx.waitTicks(2);
			List<Letter> again = letters(screenItem(ctx).getHoverName());
			for (int n = 0; n < 12; n++) check(near(again.get(n).rgb, unified.get(n).rgb, 8), "the gradient picked up from the name gives the name its colours back, letter " + n + ": " + again);

			// --- a preset chosen with text selected colours it at once ---
			drag(ctx, name[0], name[0] + name[2] + 3, name[1]);
			choose(ctx, 3);
			ctx.waitTicks(2);
			List<Letter> fire = letters(screenItem(ctx).getHoverName());
			check(fire.get(0).rgb == 0xFFE259 && fire.get(3).rgb == 0xFF5555 && fire.get(4).equals(again.get(4)) && fire.get(11).equals(again.get(11)),
				"a preset chosen with a word selected colours that word: " + fire);
			ctx.takeScreenshot("13_preset_applied");

			// --- presets: none, another name, deleted after a question, a new one ---
			choose(ctx, 0);
			check(!ctx.computeOnClient(mc -> active(mc, "nbteditorgradients.gradient.preset.delete")), "with no preset chosen there is none to delete");
			choose(ctx, 2);
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.preset.rename"));
			ctx.waitTicks(3);
			ctx.getInput().typeChars("2");
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> Presets.find("Rainbow2") != null && Presets.find("Rainbow") == null && Presets.all().size() == 3), "Rename gives the preset the name typed");
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.preset.delete"));
			ctx.waitTicks(3);
			check(overlay(ctx) && ctx.computeOnClient(mc -> Presets.all().size() == 3), "Delete asks before it deletes");
			ctx.takeScreenshot("14_delete_question");
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> Presets.all().size() == 2 && Presets.find("Rainbow2") == null), "Delete removes the preset once the answer is yes");
			press(ctx, mc -> panel(mc), tr(ctx, "nbteditorgradients.gradient.preset.new"));
			ctx.waitTicks(3);
			ctx.getInput().typeChars("Essai");
			ctx.waitTicks(2);
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(3);
			check(ctx.computeOnClient(mc -> Presets.all().size() == 3 && Presets.find("Essai") != null), "New saves the gradient under the name typed");
			click(ctx, ctx.computeOnClient(mc -> middle(panel(mc).presetsMenu())), InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(2);
			ctx.takeScreenshot("15_presets");

			// --- presets go from one instance to another as files that have their names ---
			check(button(ctx, tr(ctx, "nbteditorgradients.gradient.preset.import")) != null && button(ctx, tr(ctx, "nbteditorgradients.gradient.preset.export")) != null, "the panel has its Import and Export buttons");
			check(ctx.computeOnClient(mc -> {
				java.io.File folder = new java.io.File(mc.gameDirectory, "exported presets");
				folder.mkdirs();
				if (Presets.export(List.copyOf(Presets.all()), folder.getPath()) != 3 || !new java.io.File(folder, "Essai.json").isFile() || !new java.io.File(folder, "Birdflop.json").isFile()) return false;
				String[] colors = Presets.find("Essai").colors;
				Presets.delete("Essai");
				return Presets.load(new java.io.File(folder, "Essai.json").getPath(), new java.io.File(folder, "none.json").getPath()) == 1
					&& Presets.all().size() == 3 && java.util.Arrays.equals(Presets.find("Essai").colors, colors)
					// the presets file of an instance is read as well, and replaces the presets that have its names
					&& Presets.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("nbteditorgradients-presets.json").toString()) == 3 && Presets.all().size() == 3;
			}), "Export writes one file per preset, named as the preset; Import reads them back");

			// --- the colour spaces: each one starts and ends on the two colours ---
			for (Gradient.Type type : Gradient.Type.values()) {
				for (int[] pair : new int[][] {{0x54DAF4, 0x545EB6}, {0xFF0000, 0x00FF00}, {0x000000, 0xFFFFFF}, {0x808080, 0x3366CC}}) {
					check(near(Gradient.mix(type, pair[0], pair[1], 0), pair[0]) && near(Gradient.mix(type, pair[0], pair[1], 1), pair[1]),
						type + " keeps the two ends of " + Integer.toHexString(pair[0]) + " > " + Integer.toHexString(pair[1])
							+ ": " + Integer.toHexString(Gradient.mix(type, pair[0], pair[1], 0)) + " > " + Integer.toHexString(Gradient.mix(type, pair[0], pair[1], 1)));
				}
			}
			check(Gradient.mix(Gradient.Type.RGB, 0x000000, 0xFFFFFF, 0.5F) == 0x808080, "RGB: the middle of black and white is grey");
			check(near(Gradient.mix(Gradient.Type.HSL, 0xFF0000, 0x00FF00, 0.5F), 0xFFFF00), "HSL: red to green goes through yellow");
		}
	}

	private static DisplayScreenAccessor fields(Minecraft mc) {
		return (DisplayScreenAccessor) mc.screen;
	}

	private static GradientPanel panel(Minecraft mc) {
		return mc.screen.children().stream().filter(GradientPanel.class::isInstance).map(GradientPanel.class::cast).findFirst().orElse(null);
	}

	/** The items of the history of /get lostitem, the last one first. NBT Editor keeps the list to itself. */
	private static List<ItemStack> lostItems() {
		try {
			java.lang.reflect.Field history = GetLostItemCommand.class.getDeclaredField("history");
			history.setAccessible(true);
			return ((List<?>) history.get(null)).stream().map(item -> ((SingleDynamicItem) item).getItem()).toList();
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static MultiLineTextFieldWidget loreField(Minecraft mc) {
		return fields(mc).getLore().children().stream().filter(MultiLineTextFieldWidget.class::isInstance).map(MultiLineTextFieldWidget.class::cast).findFirst().orElseThrow();
	}

	/** Puts the cursor of the lore field at a place, presses a key, and gives the place the cursor went to. */
	private static int cursorAfter(ClientGameTestContext ctx, int from, int key) {
		ctx.runOnClient(mc -> loreField(mc).setCursor(from));
		ctx.getInput().pressKey(key);
		ctx.waitTicks(2);
		return ctx.computeOnClient(mc -> loreField(mc).getCursor());
	}

	private static GradientPanel panel(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> panel(mc));
	}

	/** Presses the button of this label among the widgets of a field or of the panel. */
	private static void press(ClientGameTestContext ctx, Function<Minecraft, ContainerEventHandler> parent, String label) {
		ctx.runOnClient(mc -> MVMisc.triggerButtonPress(parent.apply(mc).children().stream()
			.filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
			.map(Button.class::cast).findFirst().orElseThrow(() -> new AssertionError("no button " + label))));
	}

	private static String tr(ClientGameTestContext ctx, String key, Object... args) {
		return ctx.computeOnClient(mc -> Component.translatable(key, args).getString());
	}

	/** The middle of the button of this label in the gradient panel, null when there is none. */
	private static double[] button(ClientGameTestContext ctx, String label) {
		return ctx.computeOnClient(mc -> panel(mc).children().stream()
			.filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
			.map(Button.class::cast).findFirst().map(button -> new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + 10}).orElse(null));
	}

	/** A real click at x, y in GUI pixels. */
	private static void click(ClientGameTestContext ctx, double[] at, int button) {
		int scale = ctx.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		ctx.waitTick();
		ctx.getInput().pressMouse(button);
		ctx.waitTicks(2);
	}

	private static double[] middle(Button button) {
		return new double[] {button.getX() + button.getWidth() / 2.0, button.getY() + 10};
	}

	private static boolean active(Minecraft mc, String key) {
		String label = Component.translatable(key).getString();
		return panel(mc).children().stream().anyMatch(child -> child instanceof Button button && button.getMessage().getString().equals(label) && button.active);
	}

	/** Opens the list of presets and clicks one of its rows: 0 is no preset, 1 the first preset. */
	private static void choose(ClientGameTestContext ctx, int row) {
		click(ctx, ctx.computeOnClient(mc -> middle(panel(mc).presetsMenu())), InputConstants.MOUSE_BUTTON_LEFT);
		click(ctx, ctx.computeOnClient(mc -> {
			Button menu = panel(mc).presetsMenu();
			return new double[] {menu.getX() + 20, menu.getY() - 2 - (Presets.all().size() + 1 - row) * 16 + 8};
		}), InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** Holds the left button from one place of a line to another: a selection. */
	private static void drag(ClientGameTestContext ctx, double fromX, double toX, double y) {
		int scale = ctx.computeOnClient(mc -> (int) mc.getWindow().getGuiScale());
		ctx.getInput().setCursorPos(fromX * scale, y * scale);
		ctx.waitTick();
		ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		ctx.getInput().setCursorPos(toX * scale, y * scale);
		ctx.waitTick();
		ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static boolean overlay(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((OverlaySupportingScreen) mc.screen).getOverlay() != null);
	}

	/** The same colour, to one step of each channel: the colour spaces round. */
	private static boolean near(int a, int b) {
		return near(a, b, 1);
	}

	private static boolean near(int a, int b, int step) {
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > step) return false;
		}
		return true;
	}

	private static JsonObject birdflop() {
		try (Reader reader = new InputStreamReader(NbtEditorGradientsClientTest.class.getResourceAsStream("/birdflop.json"), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static Component parse(ClientGameTestContext ctx, String code) {
		return ctx.computeOnClient(mc -> ColorText.parse(code, mc.player.registryAccess()));
	}

	private static String text(ClientGameTestContext ctx, String code) {
		return parse(ctx, code).getString();
	}

	private static List<Letter> letters(ClientGameTestContext ctx, String code) {
		return letters(parse(ctx, code));
	}

	private static List<Letter> letters(Component text) {
		List<Letter> letters = new ArrayList<>();
		text.visit((style, part) -> {
			part.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(c ->
				letters.add(new Letter(c, style.getColor() == null ? null : style.getColor().getValue(), style.isBold(), style.isItalic())));
			return Optional.empty();
		}, Style.EMPTY.withItalic(true));
		return letters;
	}

	private static void command(ClientGameTestContext ctx, String command) {
		ctx.runOnClient(mc -> mc.player.connection.sendCommand(command));
		ctx.waitTicks(5);
	}

	private static List<Component> lore(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> mc.player.getMainHandItem().getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
	}

	/** The item as the display screen shows it, saved or not. */
	private static ItemStack screenItem(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> ((LocalItem) ((LocalEditorScreenAccessor) mc.screen).getLocalNBT()).getReadableItem().copy());
	}

	private static List<Component> screenLore(ClientGameTestContext ctx) {
		return screenItem(ctx).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines();
	}

	/** The key that types this letter on the keyboard of the machine (AZERTY: not the one Minecraft calls Z). */
	private static int key(ClientGameTestContext ctx, String letter) {
		return ctx.computeOnClient(mc -> {
			for (int key = InputConstants.KEY_A; key <= InputConstants.KEY_Z; key++) {
				if (letter.equalsIgnoreCase(InputConstants.getKey(new KeyEvent(key, 0, 0)).getDisplayName().getString())) return key;
			}
			throw new AssertionError("no key types " + letter);
		});
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("NBTEditor-Gradients test failed: " + what);
	}
}
