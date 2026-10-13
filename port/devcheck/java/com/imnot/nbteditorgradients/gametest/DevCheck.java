package com.imnot.nbteditorgradients.gametest;

import com.imnot.nbteditorgradients.GradientPanel;
import com.imnot.nbteditorgradients.Presets;
import com.imnot.nbteditorgradients.mixin.DisplayScreenAccessor;
import com.imnot.nbteditorgradients.mixin.MultiLineTextFieldAccessor;
import com.luneruniverse.minecraft.mod.nbteditor.commands.get.GetLostItemCommand;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.TextInst;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.commands.ClientCommandInternals;
import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.FormattedTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.MultiLineTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.tagreferences.ItemTagReferences;
import com.luneruniverse.minecraft.mod.nbteditor.util.SingleDynamicItem;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * No client gametest API before 1.21.4, and NBT Editor 2.0.3 is one jar for seven versions of Minecraft: this
 * drives the real game by hand, the same way in each of them. It makes a world from the title screen, names and
 * describes a sword with /iname and /ilore, then in the display screen converts the codes of a field, colours a
 * selection kept with Ctrl with the gradient editor, and goes back and forth with Ctrl+Z and Ctrl+Y. A screenshot
 * is taken along the way with the F2 of the game (build/run/devCheck/screenshots), then the game quits.
 * Keep the real mouse out of the window. Run: port/devcheck.sh &lt;version&gt;
 */
public class DevCheck implements ClientModInitializer {
	/** True while the check holds Ctrl or Shift: the game asks the keyboard, IsKeyDownMixin answers for it. */
	public static boolean ctrl, shift;

	private record Step(String what, BooleanSupplier ready, Runnable then) {}

	private final List<Step> steps = new ArrayList<>();
	private int step, ticks;
	/** The screen a button was last pressed on, not to press it twice. */
	private Screen pressedOn;
	/** The name of the display screen with its colours, before and after the gradient. */
	private String plain, coloured;

	@Override
	public void onInitializeClient() {
		Minecraft mc = Minecraft.getInstance();
		// Singleplayer, then Create New World once or twice: the list of worlds is skipped when it is empty, from 1.19.4
		step("a new world", () -> {
			if (mc.player != null) return mc.screen == null;
			if (mc.getOverlay() != null || mc.screen == null || mc.screen == pressedOn || ticks % 10 != 0) return false;
			mc.options.pauseOnLostFocus = false; // the check must go on in the world, window focused or not
			Button next = button(mc.screen.children(), mc.screen instanceof TitleScreen ? "menu.singleplayer" : "selectWorld.create");
			if (next != null) {
				pressedOn = mc.screen;
				next.onPress();
			}
			return false;
		}, () -> {});
		step("the sword", () -> ticks > 40, () -> {
			var server = mc.getSingleplayerServer();
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.setGameMode(GameType.CREATIVE); // NBT Editor only saves an item in creative
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			});
		});
		step("/iname and /ilore", () -> ticks > 20 && mc.gameMode.getPlayerMode() == GameType.CREATIVE && mc.player.getMainHandItem().getItem() == Items.DIAMOND_SWORD, () -> {
			command("iname <gradient:#ff0000:#0000ff>Lame</gradient> &lforte");
			command("ilore add &#00ff00Premiere");
			command("ilore add [COLOR=#FF5555]a supprimer[/COLOR]");
			command("ilore add {\"text\":\"json\",\"color\":\"#123456\"}");
			command("ilore remove 2");
		});
		step("the sword of the commands", () -> ticks > 20, () -> {
			ItemStack sword = mc.player.getMainHandItem();
			Component name = sword.getHoverName();
			List<Style> letters = styles(name);
			check(name.getString().equals("Lame forte") && rgb(letters.get(0)) == 0xFF0000 && rgb(letters.get(3)) == 0x0000FF && letters.get(5).isBold() && !letters.get(0).isItalic(),
				"/iname names the sword with a gradient and a bold word: " + colours(name));
			List<Component> lore = ItemTagReferences.LORE.get(sword);
			check(lore.size() == 2 && lore.get(0).getString().equals("Premiere") && rgb(styles(lore.get(0)).get(0)) == 0x00FF00
				&& lore.get(1).getString().equals("json") && rgb(styles(lore.get(1)).get(0)) == 0x123456, "/ilore add and remove leave two lines: " + lore);
			// an item that is edited goes to the history of /get lostitem, as it was and as it became
			List<ItemStack> lost = lostItems();
			check(lost.size() == 6 && lost.get(0).getHoverName().getString().equals("Lame forte") && ItemTagReferences.LORE.get(lost.get(0)).size() == 2
				&& ItemTagReferences.LORE.get(lost.get(1)).size() == 3 && !lost.get(5).getHoverName().getString().equals("Lame forte"),
				"the sword before the commands and after each of the five are in the history of /get lostitem: " + lost);
			command("factory display");
		});

		step("the display screen", () -> ticks > 10, () -> {
			check(mc.screen instanceof DisplayScreen<?>, "the display screen is open: " + mc.screen);
			check(button(mc.screen.children(), "nbteditorgradients.gradient") != null, "the display screen has the button of the gradient editor");
			// Home and End stop at the ends of the line the cursor is on; with Shift they select, with Ctrl they go to the ends of the text
			MultiLineTextFieldWidget lore = field(fields(mc).getLore());
			click(mc, lore.getX() + 12, lore.getY() + 12, 0);
			check(lore.getText().equals("Premiere\njson"), "the lore field holds the two lines of the commands: " + lore.getText());
			int[] ends = {cursorAfter(mc, lore, 11, 268), cursorAfter(mc, lore, 11, 269), cursorAfter(mc, lore, 3, 268), cursorAfter(mc, lore, 3, 269)};
			check(ends[0] == 9 && ends[1] == 13 && ends[2] == 0 && ends[3] == 8, "Home and End go to the ends of the line of the cursor: " + java.util.Arrays.toString(ends));
			shift = true;
			check(cursorAfter(mc, lore, 11, 268) == 9 && lore.getSelStart() == 9 && lore.getSelEnd() == 11, "Shift+Home selects up to the start of the line: " + lore.getSelStart() + " to " + lore.getSelEnd());
			check(cursorAfter(mc, lore, 3, 269) == 8 && lore.getSelStart() == 3 && lore.getSelEnd() == 8, "Shift+End selects up to the end of the line: " + lore.getSelStart() + " to " + lore.getSelEnd());
			check(cursorAfter(mc, lore, 11, 265) == 2 && lore.getSelStart() == 2 && lore.getSelEnd() == 11, "Shift+Up selects up to the same column of the line above: " + lore.getSelStart() + " to " + lore.getSelEnd());
			shift = false;
			check(cursorAfter(mc, lore, 2, 264) == 11, "Down keeps the column between two lines that start as wide: " + lore.getCursor());
			ctrl = true;
			ends = new int[] {cursorAfter(mc, lore, 11, 268), cursorAfter(mc, lore, 3, 269)};
			ctrl = false;
			check(ends[0] == 0 && ends[1] == 13, "Ctrl+Home and Ctrl+End go to the ends of the text: " + java.util.Arrays.toString(ends));
			lore.setCursor(0);
			fields(mc).getNameFormatted().setText(TextInst.literal("Hello World"));
			fields(mc).getLore().setText(TextInst.literal("&#FF0000Rouge <b>gras</b>"));
		});
		// the convert button has an icon and no label, as the reset button before it: it is the last of the two
		step("the convert button", () -> ticks > 3, () -> fields(mc).getLore().children().stream()
			.filter(child -> child instanceof Button button && button.getMessage().getString().isEmpty())
			.map(Button.class::cast).reduce((a, b) -> b).orElseThrow().onPress());
		step("the codes converted", () -> ticks > 3, () -> {
			Component lore = fields(mc).getLore().getText();
			List<Style> letters = styles(lore);
			check(lore.getString().equals("Rouge gras") && rgb(letters.get(0)) == 0xFF0000 && !letters.get(0).isBold() && letters.get(6).isBold(),
				"the convert button reads the codes of the field: " + colours(lore));
		});

		// more than a burst of typing later, so that the gradient is a step of its own for Ctrl+Z
		step("a selection kept with Ctrl", () -> ticks > 30, () -> {
			plain = colours(fields(mc).getNameFormatted().getText());
			MultiLineTextFieldWidget field = field(fields(mc).getNameFormatted());
			((MultiLineTextFieldAccessor) field).setSelStart(0);
			((MultiLineTextFieldAccessor) field).setSelEnd(5);
			// Ctrl + click further in the text: "Hello" stays selected beside the new cursor
			ctrl = true;
			click(mc, field.getX() + 4 + mc.font.width("Hello Wor"), field.getY() + 8, 0);
			ctrl = false;
		});
		step("the gradient editor", () -> ticks > 3, () -> {
			MultiLineTextFieldWidget field = field(fields(mc).getNameFormatted());
			check(field.getSelStart() == field.getSelEnd(), "the click reached the field, which has its cursor there and no selection of its own left");
			button(mc.screen.children(), "nbteditorgradients.gradient").onPress();
		});
		step("the gradient applied", () -> ticks > 3, () -> {
			check(mc.screen.children().get(0) instanceof GradientPanel, "the gradient editor is the first widget of the screen");
			// presets go from one instance to another as files that have their names
			check(button(mc.screen.children(), "nbteditorgradients.gradient.preset.import") != null && button(mc.screen.children(), "nbteditorgradients.gradient.preset.export") != null,
				"the gradient editor has its Import and Export buttons");
			java.io.File folder = new java.io.File(mc.gameDirectory, "exported presets");
			folder.mkdirs();
			int presets = Presets.all().size();
			Presets.Preset first = Presets.all().get(0);
			java.io.File file = new java.io.File(folder, first.name + ".json");
			check(Presets.export(List.copyOf(Presets.all()), folder.getPath()) == presets && file.isFile(), "Export writes one file per preset, named as the preset: " + java.util.Arrays.toString(folder.list()));
			Presets.delete(first.name);
			check(Presets.load(file.getPath(), new java.io.File(folder, "none.json").getPath()) == 1 && Presets.all().size() == presets && Presets.find(first.name) != null,
				"Import reads an exported preset back");
			button(mc.screen.children(), "nbteditorgradients.gradient.apply").onPress();
		});
		step("the selection coloured", () -> ticks > 3, () -> {
			Component name = fields(mc).getNameFormatted().getText();
			List<Style> letters = styles(name);
			check(name.getString().equals("Hello World") && rgb(letters.get(0)) != rgb(letters.get(4)) && rgb(letters.get(4)) != rgb(letters.get(5))
				&& rgb(letters.get(6)) == rgb(letters.get(10)), "the gradient colours the kept selection only: " + colours(name));
			coloured = colours(name);
			screenshot(mc);
		});
		step("Ctrl+Z", () -> ticks > 30, () -> key(mc, "z"));
		step("the gradient undone", () -> ticks > 3, () -> {
			check(colours(fields(mc).getNameFormatted().getText()).equals(plain), "Ctrl+Z takes the gradient back: " + colours(fields(mc).getNameFormatted().getText()));
			check(fields(mc).getLore().getText().getString().equals("Rouge gras"), "Ctrl+Z leaves the step before as it was");
			key(mc, "y");
		});
		step("the gradient redone", () -> ticks > 3, () -> {
			check(colours(fields(mc).getNameFormatted().getText()).equals(coloured), "Ctrl+Y brings the gradient back: " + colours(fields(mc).getNameFormatted().getText()));
			// the colour picker of NBT Editor, for the screenshot
			((ContainerEventHandler) mc.screen.children().get(0)).children().stream()
				.filter(child -> child instanceof Button button && button.getMessage().getString().equals("⬛"))
				.map(Button.class::cast).findFirst().orElseThrow().onPress();
		});
		// a click in the dark of its square: the ring goes where the mouse is
		step("the colour picker", () -> ticks > 5, () -> click(mc, (mc.screen.width - 196) / 2 + 100, (mc.screen.height - 152 - 24) / 2 + 118, 0));
		step("the ring of the colour picker", () -> ticks > 5, () -> screenshot(mc));
		step("the end", () -> ticks > 5, () -> {
			System.out.println("NBTEditor-Gradients dev check done");
			mc.stop();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
	}

	private void step(String what, BooleanSupplier ready, Runnable then) {
		steps.add(new Step(what, ready, then));
	}

	private void tick() {
		if (step >= steps.size()) return;
		Step now = steps.get(step);
		if (++ticks > 3600) fail("stuck waiting for " + now.what);
		if (!now.ready.getAsBoolean()) return;
		System.out.println("NBTEditor-Gradients dev check: " + now.what);
		now.then.run();
		step++;
		ticks = 0;
	}

	private static void check(boolean ok, String what) {
		if (!ok) fail(what);
	}

	private static void fail(String what) {
		throw new AssertionError("NBTEditor-Gradients dev check failed: " + what);
	}

	/** A client command, as NBT Editor runs the ones typed in the chat. */
	private static void command(String command) {
		ClientCommandInternals.executeCommand(command);
	}

	private static DisplayScreenAccessor fields(Minecraft mc) {
		return (DisplayScreenAccessor) mc.screen;
	}

	/** The items of the history of /get lostitem, the last one first. NBT Editor keeps the list to itself. */
	private static List<ItemStack> lostItems() {
		try {
			java.lang.reflect.Field history = GetLostItemCommand.class.getDeclaredField("history");
			history.setAccessible(true);
			List<ItemStack> items = new ArrayList<>();
			for (Object item : (List<?>) history.get(null)) items.add(((SingleDynamicItem) item).getItem());
			return items;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	/** Puts the cursor of a field at a place, presses a key, and gives the place the cursor went to. */
	private static int cursorAfter(Minecraft mc, MultiLineTextFieldWidget field, int from, int key) {
		field.setCursor(from);
		press(mc, key);
		return field.getCursor();
	}

	private static MultiLineTextFieldWidget field(FormattedTextFieldWidget box) {
		for (GuiEventListener child : box.children()) {
			if (child instanceof MultiLineTextFieldWidget field) return field;
		}
		throw new AssertionError("a formatted text field without its text field");
	}

	/** The button with this label among these widgets and the ones they hold, null when there is none. */
	private static Button button(List<? extends GuiEventListener> widgets, String key) {
		for (GuiEventListener widget : widgets) {
			if (widget instanceof Button button && button.getMessage().getString().equals(I18n.get(key))) return button;
			if (widget instanceof ContainerEventHandler group) {
				Button inside = button(group.children(), key);
				if (inside != null) return inside;
			}
		}
		return null;
	}

	/** The style of each char of a text. */
	private static List<Style> styles(Component text) {
		List<Style> styles = new ArrayList<>();
		text.visit((style, part) -> {
			for (int n = 0; n < part.length(); n++) styles.add(style);
			return Optional.empty();
		}, Style.EMPTY);
		return styles;
	}

	private static int rgb(Style style) {
		return style.getColor() == null ? -1 : style.getColor().getValue();
	}

	/** A text and the colour of each of its chars, for the message of a check that fails. */
	private static String colours(Component text) {
		StringBuilder shown = new StringBuilder(text.getString());
		for (Style style : styles(text)) shown.append(' ').append(style.getColor() == null ? "-" : Integer.toHexString(rgb(style)));
		return shown.toString();
	}

	// --- the real keyboard and mouse: the GLFW callbacks of the game ---

	/** Presses, with Ctrl, the key that types this letter on the keyboard of the machine. */
	private static void key(Minecraft mc, String typed) {
		for (int key = 65; key <= 90; key++) {
			if (!InputConstants.getKey(key, 0).getDisplayName().getString().equalsIgnoreCase(typed)) continue;
			ctrl = true;
			press(mc, key);
			ctrl = false;
			return;
		}
		fail("no key types " + typed);
	}

	private static void press(Minecraft mc, int key) {
		mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, 1, 0);
		mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), key, 0, 0, 0);
	}

	/** F2: the game saves what the last frame showed. */
	private static void screenshot(Minecraft mc) {
		press(mc, 291);
	}

	private static final Method ON_MOVE = handler("onMove", long.class, double.class, double.class);
	private static final Method ON_PRESS = handler("onPress", long.class, int.class, int.class, int.class);

	private static Method handler(String name, Class<?>... args) {
		try {
			Method m = MouseHandler.class.getDeclaredMethod(name, args);
			m.setAccessible(true);
			return m;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void call(Method m, Object... args) {
		try {
			m.invoke(Minecraft.getInstance().mouseHandler, args);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("mouse handler failed", e.getCause() != null ? e.getCause() : e);
		}
	}

	/** A click of the real mouse; x, y in GUI pixels. */
	private static void click(Minecraft mc, double x, double y, int button) {
		Window w = mc.getWindow();
		call(ON_MOVE, w.getWindow(), x * w.getScreenWidth() / w.getGuiScaledWidth(), y * w.getScreenHeight() / w.getGuiScaledHeight());
		call(ON_PRESS, w.getWindow(), button, 1, 0);
		call(ON_PRESS, w.getWindow(), button, 0, 0);
	}
}
