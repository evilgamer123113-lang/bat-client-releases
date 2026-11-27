package com.batclient.web;

import com.batclient.BatClientMod;
import com.batclient.platform.ClientResources;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.io.IOException;

public final class BatClientShaders {
	public static ShaderProgram bgraPositionTextureShader;

	public static final Identifier BGRA_POSITION_TEX = Identifier.of("batclient", "bgra_position_tex");

	public static void registerAll(ClientResources.ShaderContext context) {
		try {
			context.register(BGRA_POSITION_TEX, VertexFormats.POSITION_TEXTURE, program -> {
				bgraPositionTextureShader = program;
			});
			context.register(Identifier.of("batclient","tinted_glint"), VertexFormats.POSITION_TEXTURE, program -> com.batclient.VisualModules.glintShader=program);
		} catch (IOException e) {
			BatClientMod.LOGGER.error("Failed to register batclient:bgra_position_tex", e);
		}
	}

}
