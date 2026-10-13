package com.imnot.nbteditorgradients.mixin;

import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.MultiLineTextFieldWidget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The keys that move the cursor in a text field of NBT Editor. Home and End went to the start and the end of the
 * whole text: they now stop at the ends of the line the cursor is on, and select up to there with Shift; with Ctrl
 * they go as far as before. keyPressed is the method of Minecraft, under the name it has in the game as it is played.
 */
@Mixin(value = MultiLineTextFieldWidget.class, remap = false)
public abstract class MultiLineTextFieldMixin {
	@Shadow private String text;
	/** The text as it is laid out: a line ends with its line break, or where the field is too narrow for more. */
	@Shadow @Final private List<Component> lines;
	@Shadow private int cursor;
	/** Where Up and Down try to stay, -1 once the cursor moved another way. */
	@Shadow private int cursorX;

	@Shadow public abstract void setCursor(int cursor, boolean select);

	@Inject(method = {"keyPressed", "method_25404"}, at = @At("HEAD"), cancellable = true)
	private void nbteditorgradients$lineEnds(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		int key = event.key();
		if (key != InputConstants.KEY_HOME && key != InputConstants.KEY_END || Minecraft.getInstance().hasControlDown()) return;
		int start = 0, next = 0;
		for (Component line : lines) {
			start = next;
			next += line.getString().length();
			if (next > cursor) break;
		}
		// the last letter of a line that goes on is the line break, or a letter the cursor cannot pass without being on the next line
		int end = next > cursor && (next < text.length() || text.endsWith("\n")) ? next - 1 : next;
		setCursor(key == InputConstants.KEY_HOME ? start : end, true);
		cursorX = -1;
		cir.setReturnValue(true);
	}

	/**
	 * Up and Down look for the letter at the x of the cursor as a click would, which takes the letter before:
	 * between two lines that start alike the cursor went one letter back. One pixel further, it stays in its column.
	 */
	@ModifyArg(method = {"moveCursorUp", "moveCursorDown"}, index = 0,
		at = @At(value = "INVOKE", target = "Lcom/luneruniverse/minecraft/mod/nbteditor/screens/widgets/MultiLineTextFieldWidget;getCharPos(II)I"))
	private int nbteditorgradients$column(int x) {
		return x + 1;
	}
}
