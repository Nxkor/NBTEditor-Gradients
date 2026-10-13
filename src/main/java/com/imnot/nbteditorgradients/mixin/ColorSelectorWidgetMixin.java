package com.imnot.nbteditorgradients.mixin;

import com.imnot.nbteditorgradients.ColorCursor;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVDrawableHelper;
import com.luneruniverse.minecraft.mod.nbteditor.screens.widgets.ColorSelectorWidget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The colour picker of NBT Editor, with a ring on its square of saturation and brightness where its colour is. */
@Mixin(value = ColorSelectorWidget.class, remap = false)
public class ColorSelectorWidgetMixin {
	@Shadow @Final private int x;
	@Shadow @Final private int y;
	@Shadow @Final private int areaSize;
	@Shadow private int color;
	@Shadow private int hueValue;

	@Inject(method = "render", at = @At("TAIL"))
	private void nbteditorgradients$render(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		// the ported NBT Editor draws the square with a shader that comes out plain grey: it is drawn again over
		// it, one column per saturation, each from the full colour down to black
		var graphics = MVDrawableHelper.getDrawContext(matrices);
		for (int column = 0; column < areaSize; column++) {
			int top = 0xFF000000 | Mth.hsvToRgb(hueValue / 360.0F, (float) column / areaSize, 1.0F);
			graphics.fillGradient(x + column, y, x + column + 1, y + areaSize, top, 0xFF000000);
		}
		ColorCursor.render(matrices, x, y, areaSize, color);
	}
}
