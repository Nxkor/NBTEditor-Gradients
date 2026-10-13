package com.imnot.nbteditorgradients;

import com.luneruniverse.minecraft.mod.nbteditor.multiversion.IdentifierInst;
import com.luneruniverse.minecraft.mod.nbteditor.multiversion.MVDrawableHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.Color;

/** The small ring on the square of the colour picker of NBT Editor: where the colour it holds is in the square. */
public final class ColorCursor {
	private static float saturation, brightness;
	/** The colour the place of the ring was last worked out for. */
	private static int rgb = -1;

	private ColorCursor() {}

	/**
	 * The mouse picks this place of the square. It is kept as it is: a dark colour alone no longer tells how
	 * saturated it was picked, and black not at all.
	 */
	public static int picked(float hue, float pickedSaturation, float pickedBrightness) {
		saturation = pickedSaturation;
		brightness = pickedBrightness;
		rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
		return 0xFF000000 | rgb;
	}

	/** Draws the ring over the square at x, y; {@code color} is the colour the picker holds. */
	public static void render(PoseStack matrices, int x, int y, int size, int color) {
		if ((color & 0xFFFFFF) != rgb) {
			// a colour that was typed, or the one the picker was opened with
			rgb = color & 0xFFFFFF;
			float[] hsb = Color.RGBtoHSB(rgb >> 16, rgb >> 8 & 0xFF, rgb & 0xFF, null);
			saturation = hsb[1];
			brightness = hsb[2];
		}
		// the picture is 16 by 16 with the ring in its middle
		MVDrawableHelper.drawTexture(matrices, IdentifierInst.of("nbteditorgradients", "textures/color_cursor.png"),
			x + Math.round(saturation * size) - 8, y + Math.round((1 - brightness) * size) - 8, 0, 0, 16, 16, 16, 16);
	}
}
