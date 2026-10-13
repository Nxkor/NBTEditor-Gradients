package com.imnot.nbteditorgradients.gametest.mixin;

import com.imnot.nbteditorgradients.gametest.DevCheck;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game asks GLFW whether Ctrl or Shift is down, and no test can hold a real key: the check says when it does. */
@Mixin(InputConstants.class)
public class IsKeyDownMixin {
	@Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
	private static void nbteditorgradients$held(long window, int key, CallbackInfoReturnable<Boolean> cir) {
		if (DevCheck.ctrl && (key == 341 || key == 345) || DevCheck.shift && (key == 340 || key == 344)) cir.setReturnValue(true);
	}
}
