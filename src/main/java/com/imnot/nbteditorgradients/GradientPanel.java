package com.imnot.nbteditorgradients;

import com.imnot.nbteditorgradients.Gradient.Stop;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVDrawableHelper;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVMisc;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVTooltip;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.ColorSelectorWidget;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.GroupWidget;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.InputOverlay;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.NamedTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.StringInput;
import com.luneruniverse.minecraft.mod.nbteditor.util.TextUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * The gradient editor of the display screen: a panel docked at the bottom, over the end of the lore field. The
 * bar shows the gradient: its markers are dragged, a click on one opens its colour, a click between two adds one.
 * The top edge is dragged to resize the panel, the two tabs above it fold and close it.
 */
public class GradientPanel extends GroupWidget {
	private static final int TAB_W = 16, TAB_H = 12, PAD = 6, BAR_H = 12, ROW = 22, MAX_LENGTH = 10, MENU_ROW = 16, STRIP = 72;
	private static final int BACKGROUND = 0x99101014, BORDER = 0xFF3C3C50, BORDER_HOT = 0xFF9090C0, LABEL = 0xFFA0A0A0;
	/** The list of presets has the colours of the dropdowns of NBT Editor: black, and the row under the mouse in blue. */
	private static final int MENU = 0xFF000000, MENU_HOT = 0xFF257789;

	// one editor for the game: reopening it finds the gradient as it was left
	private static final Gradient gradient = new Gradient();
	private static Stop selected = gradient.stops.get(0);
	private static boolean folded;
	/** The height the player dragged the panel to; 0 until then: just what the controls need. */
	private static int wantedHeight;
	/** The preset the gradient was last loaded from or saved to: the one Update and Delete act on. */
	private static String activePreset;

	private final DisplayAddon.Target target;
	private final Runnable close;
	private int x, y, width, bottom, top, controlsBottom, presetsX, presetsY;
	/** The marker under the mouse button, where it went down, and whether it was dragged since. */
	private Stop pressed;
	private double pressedX;
	private boolean moved, resizing;
	private NamedTextFieldWidget hex;
	private Button pick, remove, apply, menu, update, rename, delete;
	/** What a right click does on the buttons that have a way back. */
	private final Map<Button, Runnable> previous = new IdentityHashMap<>();
	/** The colours of the strip that shows each preset in the list. */
	private final Map<Presets.Preset, int[]> strips = new IdentityHashMap<>();
	private boolean menuOpen;
	/** The first row shown, when the list has more rows than fit above its button. */
	private int menuScroll;

	// what is drawn from the gradient is only made again when the gradient or the text changed
	private int barState;
	private int[] bar = new int[0];
	private int previewState;
	private DisplayAddon.Snapshot previewOf;
	private List<Component> preview = List.of();

	public GradientPanel(DisplayAddon.Target target, Runnable close) {
		this.target = target;
		this.close = close;
	}

	public static void unfold() {
		folded = false;
	}

	/** Lays the panel out in a screen of this size; {@code top} is the highest its top edge may be dragged. */
	public void init(int screenWidth, int screenHeight, int top) {
		clearWidgets();
		previous.clear();
		this.x = 16;
		this.width = screenWidth - 32;
		this.bottom = screenHeight - 40;
		this.top = top;
		if (folded) {
			this.y = bottom;
			return;
		}
		Font font = Minecraft.getInstance().font;

		List<AbstractWidget> controls = new ArrayList<>();
		hex = new NamedTextFieldWidget(0, 0, 58, 16).name(Component.translatable("nbteditorgradients.gradient.hex"));
		hex.setMaxLength(7);
		hex.setValue(hex(selected.rgb));
		hex.setResponder(this::typeHex);
		controls.add(hex);
		controls.add(pick = button(20, Component.empty(), "pick", this::pickColor));
		controls.add(button(20, Component.literal("+"), "add", () -> select(addStop(nextGap()))));
		controls.add(remove = button(20, Component.literal("-"), "remove", () -> {
			int at = gradient.stops.indexOf(selected);
			gradient.stops.remove(selected);
			select(gradient.stops.get(Math.max(0, at - 1)));
		}));
		controls.add(button("disperse", gradient::disperse));
		controls.add(button("pickup", this::pickUp));
		Gradient.Type[] types = Gradient.Type.values();
		controls.add(option("type", () -> gradient.type.name(),
			() -> gradient.type = types[(gradient.type.ordinal() + 1) % types.length],
			() -> gradient.type = types[(gradient.type.ordinal() + types.length - 1) % types.length]));
		controls.add(option("length", () -> Integer.toString(gradient.colorLength),
			() -> gradient.colorLength = gradient.colorLength % MAX_LENGTH + 1,
			() -> gradient.colorLength = (gradient.colorLength + MAX_LENGTH - 2) % MAX_LENGTH + 1));
		controls.add(toggle("scope", () -> Component.translatable("nbteditorgradients.gradient.scope." + (gradient.perWord ? "word" : "line")).getString(), () -> gradient.perWord = !gradient.perWord));
		controls.add(toggle("spaces", () -> onOff(!gradient.skipSpaces), () -> gradient.skipSpaces = !gradient.skipSpaces));
		controls.add(toggle("lowercase", () -> onOff(gradient.lowercase), () -> gradient.lowercase = !gradient.lowercase));

		List<AbstractWidget> presets = new ArrayList<>();
		if (Presets.find(activePreset) == null) activePreset = null;
		menuOpen = false;
		menuScroll = 0;
		strips.clear();
		int menuWidth = 0;
		for (Presets.Preset preset : Presets.all()) menuWidth = Math.max(menuWidth, font.width(preset.name));
		presets.add(menu = MVMisc.newButton(0, 0, Math.max(150, 6 + STRIP + 6 + menuWidth + 6), 20, Component.empty(), btn -> menuOpen = true, new MVTooltip("nbteditorgradients.gradient.preset.menu.desc")));
		presets.add(button("preset.new", () -> askName("", name -> {
			Presets.save(name, gradient);
			activePreset = name;
		})));
		presets.add(update = button("preset.update", () -> {
			Presets.save(activePreset, gradient);
			init(width + 32, bottom + 40, this.top);
		}));
		presets.add(rename = button("preset.rename", () -> askName(activePreset, name -> {
			Presets.rename(activePreset, name);
			activePreset = name;
		})));
		presets.add(delete = button("preset.delete", () -> InputOverlay.show(Component.translatable("nbteditorgradients.gradient.preset.delete.confirm", activePreset), new Confirm(), yes -> {
			Presets.delete(activePreset);
			activePreset = null;
			init(width + 32, bottom + 40, this.top);
		})));
		presets.add(button("preset.import", this::importPresets));
		presets.add(button("preset.export", this::exportPresets));

		// the controls flow from left to right and wrap, so the panel fits a narrow window too
		int presetsIndent = font.width(Component.translatable("nbteditorgradients.gradient.presets")) + 6;
		int needed = PAD + BAR_H + 6 + flow(controls, 0, 0, false) + flow(presets, presetsIndent, 0, false) + 20 + PAD;
		this.y = bottom - Math.max(needed, Math.min(wantedHeight, bottom - top));
		presetsY = flow(controls, 0, y + PAD + BAR_H + 6, true);
		presetsX = x + PAD;
		controlsBottom = flow(presets, presetsIndent, presetsY, true);
		apply = addWidget(MVMisc.newButton(x + width - PAD - 80, bottom - PAD - 20, 80, 20, Component.translatable("nbteditorgradients.gradient.apply"),
			btn -> target.set(gradient.apply(target.get().full(), target.get().ranges())), new MVTooltip("nbteditorgradients.gradient.apply.desc")));
	}

	/** Puts the widgets in rows that start at {@code y}, the first one {@code indent} further right; returns the y under the last row. */
	private int flow(List<AbstractWidget> widgets, int indent, int y, boolean place) {
		int left = x + PAD, right = x + width - PAD;
		int cx = left + indent;
		for (AbstractWidget widget : widgets) {
			if (cx + widget.getWidth() > right && cx > left) {
				y += ROW;
				cx = left;
			}
			if (place) {
				widget.setX(cx);
				widget.setY(y + (20 - widget.getHeight()) / 2);
				addWidget(widget);
			}
			cx += widget.getWidth() + 2;
		}
		return y + ROW;
	}

	private Button button(int width, Component label, String key, Runnable action) {
		return MVMisc.newButton(0, 0, width, 20, label, btn -> action.run(), new MVTooltip("nbteditorgradients.gradient." + key + ".desc"));
	}

	/** A button as wide as its name. */
	private Button button(String key, Runnable action) {
		Component label = Component.translatable("nbteditorgradients.gradient." + key);
		return button(Minecraft.getInstance().font.width(label) + 12, label, key, action);
	}

	/** A button that shows the value of an option: a left click goes to the next value, a right click to the one before. */
	private Button option(String key, Supplier<String> value, Runnable next, Runnable before) {
		String name = "nbteditorgradients.gradient." + key;
		Font font = Minecraft.getInstance().font;
		// as wide as its longest value, so that the row does not move when the value changes
		int width = 0;
		for (int n = 0; n < MAX_LENGTH; n++) {
			width = Math.max(width, font.width(Component.translatable(name, value.get())));
			next.run();
		}
		for (int n = 0; n < MAX_LENGTH; n++) before.run();
		Button[] button = new Button[1];
		button[0] = MVMisc.newButton(0, 0, width + 12, 20, Component.translatable(name, value.get()), btn -> {
			next.run();
			btn.setMessage(Component.translatable(name, value.get()));
		}, new MVTooltip(name + ".desc"));
		previous.put(button[0], () -> {
			before.run();
			button[0].setMessage(Component.translatable(name, value.get()));
		});
		return button[0];
	}

	private Button toggle(String key, Supplier<String> value, Runnable flip) {
		return option(key, value, flip, flip);
	}

	private static String onOff(boolean on) {
		return Component.translatable(on ? "options.on" : "options.off").getString();
	}

	private static String hex(int rgb) {
		return String.format("#%06X", rgb);
	}

	/** Asks for the name of a preset, then lays the panel out again: the list of presets changed. */
	private void askName(String suggested, Consumer<String> named) {
		InputOverlay.show(Component.translatable("nbteditorgradients.gradient.preset.name"),
			StringInput.builder().withDefault(suggested).withValidator(name -> !name.isBlank()).build(), name -> {
				named.accept(name.trim());
				init(width + 32, bottom + 40, top);
			});
	}

	/** Nothing to fill in: the overlay of NBT Editor then is a question with its Ok and Cancel buttons. */
	private static class Confirm extends GroupWidget implements InputOverlay.Input<Boolean> {
		@Override
		public void init(int x, int y) {
		}

		@Override
		public int getWidth() {
			return 200;
		}

		@Override
		public int getHeight() {
			return 0;
		}

		@Override
		public Boolean getValue() {
			return true;
		}

		@Override
		public boolean isValid() {
			return true;
		}
	}

	/** Asks the system for files written by Export (or the presets file of another instance) and adds their presets to the list. */
	private void importPresets() {
		FileDialogs.files(Component.translatable("nbteditorgradients.gradient.preset.import").getString(), files -> done("imported", Presets.load(files)));
	}

	/** Writes the preset in use, or all of them when none is, in a folder asked to the system: a file each, named as in the list. */
	private void exportPresets() {
		Presets.Preset active = Presets.find(activePreset);
		List<Presets.Preset> which = active == null ? List.copyOf(Presets.all()) : List.of(active);
		FileDialogs.folder(Component.translatable("nbteditorgradients.gradient.preset.export").getString(), folder -> done("exported", Presets.export(which, folder)));
	}

	/** Says how many presets were read or written, and lays the panel out again: the list may have changed. */
	private void done(String key, int count) {
		init(width + 32, bottom + 40, top);
		InputOverlay.show(Component.translatable("nbteditorgradients.gradient.preset." + key, count), new Confirm(), ok -> {});
	}

	/** Takes the colours of a preset, or none: the gradient then belongs to no preset. Text that is selected is coloured at once. */
	private void choose(Presets.Preset preset) {
		activePreset = preset == null ? null : preset.name;
		if (preset == null) return;
		preset.load(gradient);
		select(gradient.stops.get(0));
		DisplayAddon.Snapshot now = target.get();
		if (now.selection()) target.set(gradient.apply(now.full(), now.ranges()));
	}

	/** The button that opens the list of presets: tests click it. */
	public Button presetsMenu() {
		return menu;
	}

	/** How many rows of the list fit above its button. */
	private int menuRows() {
		return Math.min(Presets.all().size() + 1, Math.max(1, (menu.getY() - 6) / MENU_ROW));
	}

	private int menuTop() {
		return menu.getY() - menuRows() * MENU_ROW - 2;
	}

	private boolean overMenu(double mouseX, double mouseY) {
		return menuOpen && mouseX >= menu.getX() && mouseX < menu.getX() + menu.getWidth() && mouseY >= menuTop() && mouseY < menu.getY();
	}

	private void typeHex(String typed) {
		String digits = typed.startsWith("#") ? typed.substring(1) : typed;
		boolean valid = digits.matches("[0-9a-fA-F]{6}");
		hex.setValid(valid);
		if (valid) selected.rgb = Integer.parseInt(digits, 16);
	}

	private void pickColor() {
		Stop stop = selected;
		InputOverlay.show(Component.translatable("nbteditorgradients.gradient.pick"), new ColorSelectorWidget.ColorSelectorInput(stop.rgb), rgb -> stop.rgb = rgb & 0xFFFFFF);
	}

	/** Takes the colours the letters of the target already have, and makes the gradient that gives them. */
	private void pickUp() {
		DisplayAddon.Snapshot from = target.get();
		List<Integer> colors = new ArrayList<>();
		int[] at = {0};
		from.full().visit((style, part) -> {
			part.codePoints().forEach(c -> {
				if (from.holds(at[0]) && !Character.isWhitespace(c) && style.getColor() != null) colors.add(style.getColor().getValue());
				at[0] += Character.charCount(c);
			});
			return Optional.empty();
		}, Style.EMPTY);
		if (colors.isEmpty()) return;
		gradient.fit(colors);
		activePreset = null;
		select(gradient.stops.get(0));
	}

	private void select(Stop stop) {
		selected = stop;
		if (hex != null) hex.setValue(hex(stop.rgb));
	}

	private Stop addStop(float pos) {
		Stop stop = new Stop(gradient.at(pos), pos);
		gradient.stops.add(stop);
		gradient.sort();
		return stop;
	}

	/** The middle of the widest gap between two stops: where the + button puts the new one. */
	private float nextGap() {
		float best = 0.5F, widest = -1;
		float before = 0;
		for (int n = 0; n <= gradient.stops.size(); n++) {
			float after = n == gradient.stops.size() ? 1 : gradient.stops.get(n).pos;
			if (after - before > widest) {
				widest = after - before;
				best = (before + after) / 2;
			}
			before = after;
		}
		return best;
	}

	private int barLeft() {
		return x + PAD + 3;
	}

	private int barWidth() {
		return width - 2 * PAD - 6;
	}

	private int barTop() {
		return y + PAD;
	}

	private int markerX(Stop stop) {
		return barLeft() + Math.round(stop.pos * barWidth());
	}

	private boolean overTab(double mouseX, double mouseY, int tab) {
		return mouseX >= x + tab * TAB_W && mouseX < x + (tab + 1) * TAB_W && mouseY >= y - TAB_H && mouseY < y;
	}

	private boolean overEdge(double mouseX, double mouseY) {
		return !folded && mouseX >= x + 2 * TAB_W && mouseX < x + width && mouseY >= y - 2 && mouseY < y + 3;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return overTab(mouseX, mouseY, 0) || overTab(mouseX, mouseY, 1) || overEdge(mouseX, mouseY) || overMenu(mouseX, mouseY)
			|| !folded && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < bottom;
	}

	/** Folds the panel down to its two tabs, or unfolds it. */
	public void fold() {
		folded = !folded;
		init(width + 32, bottom + 40, top);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mouseX = click.x(), mouseY = click.y();
		if (overTab(mouseX, mouseY, 0)) {
			fold();
			return true;
		}
		if (overTab(mouseX, mouseY, 1)) {
			close.run();
			return true;
		}
		if (menuOpen) {
			// a click anywhere closes the list; on one of its rows, it chooses first
			boolean chosen = overMenu(mouseX, mouseY);
			int row = (int) (mouseY - menuTop() - 1) / MENU_ROW + menuScroll;
			menuOpen = false;
			if (chosen && row <= Presets.all().size()) choose(row == 0 ? null : Presets.all().get(row - 1));
			if (chosen || menu.isMouseOver(mouseX, mouseY)) return true;
		}
		if (!isMouseOver(mouseX, mouseY)) return false;
		if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			for (Map.Entry<Button, Runnable> back : previous.entrySet()) {
				if (back.getKey().isMouseOver(mouseX, mouseY)) back.getValue().run();
			}
			return true;
		}
		if (overEdge(mouseX, mouseY)) {
			resizing = true;
			return true;
		}
		if (mouseY >= barTop() - 3 && mouseY < barTop() + BAR_H + 3) {
			Stop hit = null;
			for (Stop stop : gradient.stops) {
				if (Math.abs(mouseX - markerX(stop)) <= 4 && (hit == null || stop == selected)) hit = stop;
			}
			// a marker that is not dragged opens its colour when the button comes up; a new one does not
			moved = hit == null;
			pressed = hit != null ? hit : addStop(barPos(mouseX));
			pressedX = mouseX;
			select(pressed);
			return true;
		}
		super.mouseClicked(click, doubled);
		return true; // the lore field under the panel must not get the click
	}

	private float barPos(double mouseX) {
		return (float) Math.max(0, Math.min(1, (mouseX - barLeft()) / barWidth()));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
		if (resizing) {
			wantedHeight = (int) Math.max(0, bottom - click.y());
			init(width + 32, bottom + 40, top);
			return true;
		}
		if (pressed != null) {
			moved |= Math.abs(click.x() - pressedX) > 2;
			if (moved) {
				pressed.pos = barPos(click.x());
				gradient.sort();
			}
			return true;
		}
		return super.mouseDragged(click, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!overMenu(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		menuScroll = Math.max(0, Math.min(Presets.all().size() + 1 - menuRows(), menuScroll - (int) Math.signum(scrollY)));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		resizing = false;
		Stop clicked = moved ? null : pressed;
		pressed = null;
		if (clicked != null) {
			pickColor();
			return true;
		}
		return super.mouseReleased(click);
	}

	@Override
	public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		tab(matrices, 0, folded ? "+" : "-", mouseX, mouseY);
		tab(matrices, 1, "x", mouseX, mouseY);
		MVDrawableHelper.drawTextWithShadow(matrices, font, Component.translatable("nbteditorgradients.gradient.title"), x + 2 * TAB_W + 4, y - TAB_H + 2, LABEL);
		if (folded) {
			MVDrawableHelper.fill(matrices, x, y - 1, x + width, y, BORDER);
			return;
		}
		boolean hot = resizing || overEdge(mouseX, mouseY);
		MVDrawableHelper.fill(matrices, x, y, x + width, bottom, BACKGROUND);
		MVDrawableHelper.fill(matrices, x, y, x + width, y + 1, hot ? BORDER_HOT : BORDER);
		MVDrawableHelper.fill(matrices, x, bottom - 1, x + width, bottom, BORDER);
		MVDrawableHelper.fill(matrices, x, y, x + 1, bottom, BORDER);
		MVDrawableHelper.fill(matrices, x + width - 1, y, x + width, bottom, BORDER);

		// the gradient, then its stops over it
		int state = gradient.state();
		int left = barLeft(), barTop = barTop(), slices = (barWidth() + 1) / 2;
		if (state != barState || bar.length != slices) {
			Gradient.Sampler sampler = gradient.sampler();
			bar = new int[slices];
			for (int n = 0; n < slices; n++) bar[n] = 0xFF000000 | sampler.at((2 * n + 1F) / barWidth());
			barState = state;
		}
		for (int n = 0; n < slices; n++) {
			MVDrawableHelper.fill(matrices, left + 2 * n, barTop, Math.min(left + 2 * n + 2, left + barWidth()), barTop + BAR_H, bar[n]);
		}
		for (Stop stop : gradient.stops) {
			if (stop != selected) marker(matrices, stop, 0xFF000000);
		}
		marker(matrices, selected, 0xFFFFFFFF);

		DisplayAddon.Snapshot now = target.get();
		boolean hasText = now.ranges().stream().anyMatch(range -> range[1] > range[0]);
		remove.active = gradient.stops.size() > 1;
		apply.active = hasText;
		Presets.Preset active = Presets.find(activePreset);
		update.active = rename.active = delete.active = active != null;
		pick.setMessage(Component.literal("⬛").withColor(selected.rgb));
		// a colour picked or dragged elsewhere shows in the field, unless the player is typing in it
		if (!hex.isFocused() && !hex.getValue().equals(hex(selected.rgb))) hex.setValue(hex(selected.rgb));
		MVDrawableHelper.drawTextWithShadow(matrices, font, Component.translatable("nbteditorgradients.gradient.presets"), presetsX, presetsY + 6, LABEL);
		super.render(matrices, mouseX, mouseY, delta);
		preset(matrices, active, menu.getX() + 6, menu.getY() + 5, false, -1);
		for (int n = 0; n < 3; n++) {
			MVDrawableHelper.fill(matrices, menu.getX() + menu.getWidth() - 12 + n, menu.getY() + 9 + n, menu.getX() + menu.getWidth() - 7 - n, menu.getY() + 10 + n, -1);
		}

		// what Apply would write: the lines it touches, as many as the panel has room for
		if (previewOf == null || state != previewState || !now.same(previewOf)) {
			preview = hasText ? touched(gradient.apply(now.full(), now.ranges()), now.ranges()) : List.of();
			previewOf = now;
			previewState = state;
		}
		int lineY = controlsBottom + 6;
		MVDrawableHelper.drawTextWithShadow(matrices, font, now.label(), x + PAD, lineY, LABEL);
		int textX = x + PAD + font.width(now.label()) + 6;
		MVDrawableHelper.enableScissor(matrices, textX, controlsBottom, x + width - PAD - 84 - textX, bottom - PAD - controlsBottom);
		for (Component line : preview) {
			if (lineY + 9 > bottom - PAD) break;
			MVDrawableHelper.drawTextWithShadow(matrices, font, line, textX, lineY, -1);
			lineY += 10;
		}
		MVDrawableHelper.disableScissor(matrices);

		// the list of presets, over everything else: it goes up from its button, out of the panel
		if (menuOpen) {
			int menuTop = menuTop(), menuLeft = menu.getX(), menuRight = menuLeft + menu.getWidth();
			MVDrawableHelper.fill(matrices, menuLeft, menuTop, menuRight, menu.getY(), BORDER);
			MVDrawableHelper.fill(matrices, menuLeft + 1, menuTop + 1, menuRight - 1, menu.getY() - 1, MENU);
			for (int n = 0; n < menuRows(); n++) {
				int row = n + menuScroll, rowTop = menuTop + 1 + n * MENU_ROW;
				boolean hover = mouseX >= menuLeft && mouseX < menuRight && mouseY >= rowTop && mouseY < rowTop + MENU_ROW;
				Presets.Preset preset = row == 0 ? null : Presets.all().get(row - 1);
				preset(matrices, preset, menuLeft + 6, rowTop + 3, preset != null && preset == active, hover ? MENU_HOT : -1);
			}
		}
	}

	/** A preset as the list and its button show it: a strip of its colours in a frame, then its name; null is the row of no preset. */
	private void preset(PoseStack matrices, Presets.Preset preset, int left, int top, boolean chosen, int color) {
		Font font = Minecraft.getInstance().font;
		if (preset == null) {
			MVDrawableHelper.drawTextWithShadow(matrices, font, Component.translatable("nbteditorgradients.gradient.preset.none"), left, top + 1, color == -1 ? LABEL : color);
			return;
		}
		// the frame of the preset in use is white, and wider than the others
		MVDrawableHelper.fill(matrices, left - 2, top - 2, left + STRIP + 2, top + 12, chosen ? -1 : 0);
		MVDrawableHelper.fill(matrices, left - 1, top - 1, left + STRIP + 1, top + 11, chosen ? -1 : BORDER);
		int[] strip = strips.computeIfAbsent(preset, GradientPanel::strip);
		for (int n = 0; n < strip.length; n++) MVDrawableHelper.fill(matrices, left + 2 * n, top, left + 2 * n + 2, top + 10, strip[n]);
		MVDrawableHelper.drawTextWithShadow(matrices, font, Component.literal(preset.name), left + STRIP + 6, top + 1, color);
	}

	/** The colours of the strip of a preset, worked out the first time it is drawn. */
	private static int[] strip(Presets.Preset preset) {
		Gradient colors = new Gradient();
		preset.load(colors);
		Gradient.Sampler sampler = colors.sampler();
		int[] strip = new int[STRIP / 2];
		for (int n = 0; n < strip.length; n++) strip[n] = 0xFF000000 | sampler.at((n + 0.5F) / strip.length);
		return strip;
	}

	/** The lines of a text that hold a part of one of the ranges. */
	private static List<Component> touched(Component text, List<int[]> ranges) {
		List<Component> lines = new ArrayList<>();
		int start = 0;
		for (Component line : TextUtil.splitText(text)) {
			int end = start + line.getString().length();
			for (int[] range : ranges) {
				if (range[1] > range[0] && range[0] <= end && range[1] >= start) {
					lines.add(line);
					break;
				}
			}
			start = end + 1;
		}
		return lines;
	}

	private void tab(PoseStack matrices, int tab, String label, int mouseX, int mouseY) {
		int left = x + tab * TAB_W;
		boolean hot = overTab(mouseX, mouseY, tab);
		MVDrawableHelper.fill(matrices, left, y - TAB_H, left + TAB_W, y, hot ? BORDER_HOT : BORDER);
		MVDrawableHelper.fill(matrices, left + 1, y - TAB_H + 1, left + TAB_W - 1, y, BACKGROUND);
		MVDrawableHelper.drawCenteredTextWithShadow(matrices, Minecraft.getInstance().font,
			Component.literal(label).withStyle(hot ? ChatFormatting.WHITE : ChatFormatting.GRAY), left + TAB_W / 2, y - TAB_H + 2, -1);
	}

	private void marker(PoseStack matrices, Stop stop, int outline) {
		int at = markerX(stop), barTop = barTop();
		MVDrawableHelper.fill(matrices, at - 3, barTop - 2, at + 3, barTop + BAR_H + 2, outline);
		MVDrawableHelper.fill(matrices, at - 2, barTop - 1, at + 2, barTop + BAR_H + 1, 0xFF000000 | stop.rgb);
	}
}
