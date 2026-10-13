package com.imnot.nbteditorgradients.mixin;

import com.imnot.nbteditorgradients.ColorCursor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The square of the colour picker: a click or a drag in it makes a colour of where the mouse is. The mod keeps
 * that place for its ring. The method is the one of Minecraft, under the name it has in the game as it is played.
 */
@Mixin(targets = "com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.ColorSelectorWidget$ColorArea", remap = false)
public class ColorAreaMixin {
	@Redirect(method = {"mouseDragged", "method_25403"}, at = @At(value = "INVOKE", target = "Ljava/awt/Color;HSBtoRGB(FFF)I"))
	private int nbteditorgradients$picked(float hue, float saturation, float brightness) {
		return ColorCursor.picked(hue, saturation, brightness);
	}
}
