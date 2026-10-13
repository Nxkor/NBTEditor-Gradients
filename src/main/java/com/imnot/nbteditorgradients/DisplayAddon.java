package com.imnot.nbteditorgradients;

import com.imnot.nbteditorgradients.mixin.DisplayScreenAccessor;
import com.imnot.nbteditorgradients.mixin.LocalEditorScreenAccessor;
import com.imnot.nbteditorgradients.mixin.MultiLineTextFieldAccessor;
import com.luneruniverse.minecraft.mod.nbteditor.async.ItemSize;
import com.luneruniverse.minecraft.mod.nbteditor.localnbt.LocalItem;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVDrawableHelper;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVMisc;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVTooltip;
import com.luneruniverse.minecraft.mod.nbteditor.screens.ConfigScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.FormattedTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.MultiLineTextFieldWidget;
import com.luneruniverse.minecraft.mod.nbteditor.util.TextUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What the mod adds to the display screen of NBT Editor: a button on each text field that turns the colour codes
 * typed in it into coloured text, the gradient editor, and the weight of the item next to the save button.
 */
public final class DisplayAddon {
	/**
	 * What the gradient would colour right now: a text, and the parts of it (start and end, in chars) that are
	 * chosen; {@code selection} is false when nothing is selected and the whole text stands for it.
	 */
	public record Snapshot(Component full, List<int[]> ranges, boolean selection, Component label) {
		public boolean holds(int pos) {
			for (int[] range : ranges) {
				if (pos >= range[0] && pos < range[1]) return true;
			}
			return false;
		}

		/** The same text and the same parts: what was drawn from the other one still holds. */
		public boolean same(Snapshot other) {
			if (full != other.full || ranges.size() != other.ranges.size()) return false;
			for (int n = 0; n < ranges.size(); n++) {
				if (ranges.get(n)[0] != other.ranges.get(n)[0] || ranges.get(n)[1] != other.ranges.get(n)[1]) return false;
			}
			return true;
		}
	}

	/** The text field the gradient editor works on: the one the player was last in. */
	public interface Target {
		Snapshot get();

		void set(Component full);
	}

	/** Two arrows in a circle, drawn on the convert buttons. */
	private static final String[] CONVERT_ICON = {
		"...#####...",
		"..#.....#.#",
		".#.......##",
		".#......###",
		"...........",
		"...........",
		"###......#.",
		"##.......#.",
		"#.#.....#..",
		"...#####...",
	};
	/** The colour NBT Editor gives the selection of its text fields. */
	private static final int SELECTION = 0x55FFFFFF;

	private static boolean gradientOpen;
	private static boolean targetLore;
	private static GradientPanel panel;
	/**
	 * The selections kept in the target field while another one is made with Ctrl held, and the text they were
	 * made in. The field itself only knows the last one.
	 */
	private static final List<int[]> kept = new ArrayList<>();
	private static String keptText = "";
	private static final List<Button> convertButtons = new ArrayList<>();
	private static ItemStack weighed = ItemStack.EMPTY;
	/** The weight as it is drawn; null when it has to be asked again. */
	private static Component weight;

	private DisplayAddon() {}

	/** Called at the end of each init of the display screen: its widgets are new. */
	public static void init(DisplayScreen<?> screen) {
		FormattedTextFieldWidget name = ((DisplayScreenAccessor) screen).getNameFormatted();
		FormattedTextFieldWidget lore = ((DisplayScreenAccessor) screen).getLore();
		convertButtons.clear();
		if (ConfigScreen.isHideFormatButtons()) {
			gradientOpen = false;
		} else {
			int afterName = addConvert(name);
			int afterLore = lore == null ? 0 : addConvert(lore);
			FormattedTextFieldWidget row = lore == null ? name : lore;
			Component label = Component.translatable("nbteditorgradients.gradient");
			row.addWidget(MVMisc.newButton((lore == null ? afterName : afterLore) + 4, row.getY(), Minecraft.getInstance().font.width(label) + 10, 20, label, btn -> {
				gradientOpen = !gradientOpen;
				GradientPanel.unfold();
				syncPanel(screen);
			}, new MVTooltip("nbteditorgradients.gradient.desc")));
		}
		targetLore &= lore != null;
		weight = null;
		tick(screen);
		syncPanel(screen);
	}

	/** Adds the convert button after the format buttons of a field; returns the x after it. */
	private static int addConvert(FormattedTextFieldWidget box) {
		// where NBT Editor puts its own: the 16 colours fold into one button when the field is narrow
		boolean folded = box.getWidth() < 320 + 172;
		int x = box.getX() + (folded ? 20 : 320) + 216;
		convertButtons.add(box.addWidget(MVMisc.newButton(x, box.getY(), 20, 20, Component.empty(),
			btn -> setText(box, TextUtil.joinLines(box.getTextLines().stream().map(line -> ColorText.convert(line, Minecraft.getInstance().player.registryAccess())).toList())),
			new MVTooltip("nbteditorgradients.convert", "nbteditorgradients.convert.desc"))));
		return x + 20;
	}

	/** Gives a field its new text, also when only the colours changed: the field ignores a text that reads the same. */
	private static void setText(FormattedTextFieldWidget box, Component text) {
		MultiLineTextFieldWidget field = field(box);
		int start = field.getSelStart(), end = field.getSelEnd();
		boolean same = field.getText().equals(text.getString());
		if (same) box.setText(Component.empty());
		box.setText(text);
		if (same) {
			// only the colours changed: what was selected still is, to try another gradient on it
			field.setCursor(end);
			((MultiLineTextFieldAccessor) field).setSelStart(start);
			((MultiLineTextFieldAccessor) field).setSelEnd(end);
		}
	}

	@SuppressWarnings("unchecked")
	private static void syncPanel(DisplayScreen<?> screen) {
		List<GuiEventListener> children = (List<GuiEventListener>) screen.children();
		children.remove(panel);
		panel = null;
		if (!gradientOpen) return;
		FormattedTextFieldWidget name = ((DisplayScreenAccessor) screen).getNameFormatted();
		panel = new GradientPanel(target(screen), () -> {
			gradientOpen = false;
			syncPanel(screen);
		});
		panel.init(screen.width, screen.height, name.getY() + name.getHeight() + 40);
		// first, so that a click on the panel does not reach the lore field under it
		children.add(0, panel);
	}

	private static FormattedTextFieldWidget box(DisplayScreen<?> screen) {
		DisplayScreenAccessor fields = (DisplayScreenAccessor) screen;
		return targetLore ? fields.getLore() : fields.getNameFormatted();
	}

	/** The part of a formatted field that holds the text, its cursor and its selection. */
	private static MultiLineTextFieldWidget field(FormattedTextFieldWidget box) {
		for (GuiEventListener child : box.children()) {
			if (child instanceof MultiLineTextFieldWidget field) return field;
		}
		throw new IllegalStateException("a formatted text field without its text field");
	}

	private static Target target(DisplayScreen<?> screen) {
		return new Target() {
			@Override
			public Snapshot get() {
				FormattedTextFieldWidget box = box(screen);
				MultiLineTextFieldWidget field = field(box);
				String key = "nbteditorgradients.gradient.target." + (targetLore ? "lore" : "name");
				List<int[]> ranges = new ArrayList<>(kept);
				if (field.getSelStart() != field.getSelEnd()) ranges.add(new int[] {field.getSelStart(), field.getSelEnd()});
				if (ranges.isEmpty()) return new Snapshot(box.getText(), List.of(new int[] {0, field.getText().length()}), false, Component.translatable(key));
				Component label = ranges.size() == 1 ? Component.translatable(key + ".selection") : Component.translatable(key + ".selections", ranges.size());
				return new Snapshot(box.getText(), ranges, true, label);
			}

			@Override
			public void set(Component full) {
				setText(box(screen), full);
			}
		};
	}

	/**
	 * A click on the display screen, before the screen gets it. With Ctrl held, a click in a field starts another
	 * selection: the one the field had is kept beside it. A Ctrl + click inside a kept selection takes it out, a
	 * click without Ctrl forgets them all. The field then gets the click as usual.
	 */
	public static void click(DisplayScreen<?> screen, double mouseX, double mouseY) {
		if (screen.getOverlay() != null || panel != null && panel.isMouseOver(mouseX, mouseY)) return;
		DisplayScreenAccessor fields = (DisplayScreenAccessor) screen;
		for (FormattedTextFieldWidget box : new FormattedTextFieldWidget[] {fields.getNameFormatted(), fields.getLore()}) {
			if (box == null) continue;
			MultiLineTextFieldWidget field = field(box);
			if (!field.isMouseOver(mouseX, mouseY)) continue;
			String text = field.getText();
			boolean lore = box == fields.getLore();
			if (!Minecraft.getInstance().hasControlDown() || lore != targetLore || !text.equals(keptText)) kept.clear();
			if (!Minecraft.getInstance().hasControlDown()) return;
			targetLore = lore;
			keptText = text;
			MultiLineTextFieldAccessor inside = (MultiLineTextFieldAccessor) field;
			int at = inside.invokeGetCharPos((int) mouseX, (int) mouseY - inside.getScroll());
			if (kept.removeIf(range -> at >= range[0] && at < range[1])) return;
			if (field.getSelStart() != field.getSelEnd()) kept.add(new int[] {field.getSelStart(), field.getSelEnd()});
			return;
		}
	}
	/** Each tick of the display screen. */
	public static void tick(DisplayScreen<?> screen) {
		DisplayScreenAccessor fields = (DisplayScreenAccessor) screen;
		if (!kept.isEmpty()) {
			// selections kept in a text that was edited since are no longer where they were
			if (!field(box(screen)).getText().equals(keptText)) kept.clear();
		} else if (screen.getFocused() == fields.getNameFormatted()) {
			// the gradient follows the field the player clicks in
			targetLore = false;
		} else if (fields.getLore() != null && screen.getFocused() == fields.getLore()) {
			targetLore = true;
		}
		// NBT Editor counts in the background and remembers per stack: a stack edited in place needs a new copy
		if (((LocalEditorScreenAccessor) screen).getLocalNBT() instanceof LocalItem item && !ItemStack.matches(weighed, item.getReadableItem())) {
			weighed = item.getReadableItem().copy();
			weight = null;
		}
	}

	/** Drawn after the screen and its fields. */
	public static void render(DisplayScreen<?> screen, PoseStack matrices, int mouseX, int mouseY, float delta) {
		for (Button button : convertButtons) {
			for (int row = 0; row < CONVERT_ICON.length; row++) {
				for (int col = 0; col < CONVERT_ICON[row].length(); col++) {
					if (CONVERT_ICON[row].charAt(col) == '#') {
						MVDrawableHelper.fill(matrices, button.getX() + 5 + col, button.getY() + 5 + row, button.getX() + 6 + col, button.getY() + 6 + row, -1);
					}
				}
			}
		}
		if (!kept.isEmpty()) {
			MultiLineTextFieldWidget field = field(box(screen));
			MultiLineTextFieldAccessor inside = (MultiLineTextFieldAccessor) field;
			MVDrawableHelper.enableScissor(matrices, field.getX(), field.getY(), field.getWidth(), field.getHeight());
			matrices.pushPose();
			matrices.translate(0.0F, inside.getScroll(), 0.0F);
			for (int[] range : kept) inside.invokeRenderHighlight(matrices, range[0], range[1], SELECTION);
			matrices.popPose();
			MVDrawableHelper.disableScissor(matrices);
		}
		if (((LocalEditorScreenAccessor) screen).getLocalNBT() instanceof LocalItem) {
			MVDrawableHelper.drawTextWithShadow(matrices, Minecraft.getInstance().font, weight != null ? weight : weight(), 312, 28, -1);
		}
		if (panel != null) panel.render(matrices, mouseX, mouseY, delta);
	}

	/** The weight of the item as its tooltip gives it; asked again each frame only while NBT Editor is still counting. */
	private static Component weight() {
		ConfigScreen.ItemSizeFormat format = ConfigScreen.getItemSizeFormat();
		OptionalLong counted = ItemSize.getItemSize(weighed, format.isCompressed());
		Component value;
		if (counted.isEmpty()) {
			value = Component.literal("...").withStyle(ChatFormatting.GRAY);
		} else {
			long size = counted.getAsLong();
			int unit = format.getMagnitude() > 0 ? format.getMagnitude() : size < 1000 ? 1 : size < 1000000 ? 1000 : size < 1000000000 ? 1000000 : 1000000000;
			String number = unit == 1 ? Long.toString(size) : String.format("%.1f", (double) size / unit);
			value = switch (unit) {
				case 1 -> Component.literal(number + "B").withStyle(ChatFormatting.GREEN);
				case 1000 -> Component.literal(number + "KB").withStyle(ChatFormatting.YELLOW);
				case 1000000 -> Component.literal(number + "MB").withStyle(ChatFormatting.RED);
				default -> Component.literal(number + "GB").withStyle(ChatFormatting.DARK_RED);
			};
		}
		Component text = Component.translatable("nbteditorgradients.weight", value).withStyle(ChatFormatting.GRAY);
		// while the count is not there, the next frame asks again
		if (counted.isPresent()) weight = text;
		return text;
	}
}
