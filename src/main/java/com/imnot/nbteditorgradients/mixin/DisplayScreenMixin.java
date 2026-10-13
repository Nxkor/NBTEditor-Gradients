package com.imnot.nbteditorgradients.mixin;

import com.imnot.nbteditorgradients.DisplayAddon;
import com.luneruniverse.minecraft.mod.nbteditor.screens.factories.DisplayScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both methods are NBT Editor's own: the screen calls initEditor at each init, renderEditor after its widgets are drawn. */
@Mixin(value = DisplayScreen.class, remap = false)
public class DisplayScreenMixin {
	@Inject(method = "initEditor", at = @At("TAIL"))
	private void nbteditorgradients$init(CallbackInfo ci) {
		DisplayAddon.init((DisplayScreen<?>) (Object) this);
	}

	@Inject(method = "renderEditor", at = @At("TAIL"))
	private void nbteditorgradients$render(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		DisplayAddon.render((DisplayScreen<?>) (Object) this, matrices, mouseX, mouseY, delta);
	}
}
