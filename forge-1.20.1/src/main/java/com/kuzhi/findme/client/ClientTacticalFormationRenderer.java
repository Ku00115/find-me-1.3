package com.kuzhi.findme.client;

import com.kuzhi.findme.client.ClientMagicCircleRenderer.Color;
import com.kuzhi.findme.network.CompanionTacticalFormationPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

final class ClientTacticalFormationRenderer {
    private static final Color BLUE = new Color(0.48f, 0.86f, 1.0f);
    private static final Color WHITE = new Color(0.98f, 1.0f, 1.0f);

    private ClientTacticalFormationRenderer() {
    }

    static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || ClientTacticalFormationState.effects().isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        pose.pushPose();
        pose.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (ClientTacticalFormationState.Effect effect : ClientTacticalFormationState.effects()) {
            renderEffect(buffer, matrix, effect, event.getPartialTick());
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static void renderEffect(BufferBuilder buffer, Matrix4f matrix,
                                     ClientTacticalFormationState.Effect effect, float partialTick) {
        float age = effect.age + partialTick;
        float progress = Mth.clamp(age / effect.durationTicks, 0.0f, 1.0f);
        float fade = 1.0f - smooth(Mth.clamp((progress - 0.78f) / 0.22f, 0.0f, 1.0f));
        float centerOpen = smooth(Mth.clamp(age / 8.0f, 0.0f, 1.0f));
        float alpha = centerOpen * fade;
        ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, effect.center,
                effect.centerRadius * (0.15 + centerOpen * 0.85), BLUE, 0.72f * alpha,
                progress, age * 1.1f, 0.48);
        for (CompanionTacticalFormationPacket.Member member : effect.members) {
            float localAge = age - member.revealOffsetTicks();
            if (localAge < 0.0f) continue;
            float open = smooth(Mth.clamp(localAge / 7.0f, 0.0f, 1.0f));
            Vec3 ground = new Vec3(member.x(), member.y() + 0.08, member.z());
            Vec3 lineStart = effect.center.add(0.0, 0.10, 0.0);
            ClientMagicCircleRenderer.line(buffer, matrix, lineStart, ground,
                    Math.max(0.025, member.radius() * 0.014), WHITE, 0.60f * open * fade);
            double overhead = member.y() + Math.max(3.0, member.radius() * 1.65);
            Vec3 circle = new Vec3(member.x(), overhead, member.z());
            ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, circle,
                    member.radius() * (0.12 + open * 0.88), BLUE, 0.82f * open * fade,
                    progress, localAge * 1.35f, 0.62);
            ClientMagicCircleRenderer.renderGroundCircle(buffer, matrix, circle.add(0.0, -0.035, 0.0),
                    member.radius() * (0.10 + open * 0.82), WHITE, 0.30f * open * fade,
                    progress, -localAge * 0.9f, 0.38);
        }
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
}
