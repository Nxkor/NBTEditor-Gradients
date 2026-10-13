package com.imnot.nbteditorgradients.mixin;

import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.MultiLineTextFieldWidget;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What the text field of NBT Editor knows and keeps to itself: which letter is under the mouse, and where a letter is drawn. */
@Mixin(value = MultiLineTextFieldWidget.class, remap = false)
public interface MultiLineTextFieldAccessor {
	/** How far the text is scrolled, as the field adds it to the y of its lines. */
	@Accessor("scroll")
	int getScroll();

	/** Where the selection was started; its other end is where the cursor is. */
	@Accessor("selStart")
	void setSelStart(int start);

	@Accessor("selEnd")
	void setSelEnd(int end);

	/** The index of the letter at this place, where {@code mouseY} is taken before the scroll. */
	@Invoker("getCharPos")
	int invokeGetCharPos(int mouseX, int mouseY);

	/** Draws a highlight behind the letters from {@code start} to {@code end}, in the place they have before the scroll. */
	@Invoker("renderHighlight")
	void invokeRenderHighlight(PoseStack matrices, int start, int end, int color);
}
