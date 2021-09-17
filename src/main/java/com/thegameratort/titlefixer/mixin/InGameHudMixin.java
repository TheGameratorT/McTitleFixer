package com.thegameratort.titlefixer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thegameratort.titlefixer.TitleFixer;
import com.thegameratort.titlefixer.TitleRenderInfo;
import com.thegameratort.titlefixer.config.ScoreboardMode;
import com.thegameratort.titlefixer.config.TitleFixerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Final @Shadow private MinecraftClient client;
    @Shadow private int titleTotalTicks;
    @Shadow private Text title;
    @Shadow private Text subtitle;
    @Shadow private int titleFadeInTicks;
    @Shadow private int titleRemainTicks;
    @Shadow private int titleFadeOutTicks;
    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;

    @Shadow public abstract TextRenderer getFontRenderer();
    @Shadow protected abstract void drawTextBackground(MatrixStack matrices, TextRenderer textRenderer, int yOffset, int width, int color);

    private Text titlec;

    private int scoreboardWidth = -1;
    private int scoreboardOpacityGain = 0;

    public boolean renderTitle = false;
    public boolean hideScoreboard = false;
    public final TitleRenderInfo titleRI = new TitleRenderInfo();
    public final TitleRenderInfo subtitleRI = new TitleRenderInfo();

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"))
    private void preRenderHud(CallbackInfo ci) {
        scoreboardWidth = -1; // reset variable
        hideScoreboard = false; // reset variable
        titlec = title; // keep a reference for the title
        title = null; // prevent operation of the original title code
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void postRenderHud(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        /* Calculate title stuff */
        collectRenderInfo();
        /* Render the title */
        executeRenderInfo(matrices, tickDelta);

        title = titlec; // restore the title
    }

    private void collectRenderInfo() {
        renderTitle = titlec != null && titleTotalTicks > 0;
        if (renderTitle) {
            TitleFixerConfig config = TitleFixer.getConfig();
            TextRenderer textRenderer = getFontRenderer();

            int titleWidth = textRenderer.getWidth(titlec);
            collectTitleRenderInfo(titleRI, config.preferredTitleScale, titleWidth, config);

            if (subtitle != null) {
                int subtitleWidth = textRenderer.getWidth(subtitle);
                collectTitleRenderInfo(subtitleRI, config.preferredSubtitleScale, subtitleWidth, config);
            }
        }
    }

    private void collectTitleRenderInfo(TitleRenderInfo ri, float titleScale, int titleWidth, TitleFixerConfig config) {
        float renderScale = titleScale;
        int renderAreaWidth = scaledWidth - config.titleMarginLeft - config.titleMarginRight;
        int renderAreaWidthSB = renderAreaWidth - scoreboardWidth;
        if (config.scoreboardMode == ScoreboardMode.FADE) {
            renderAreaWidthSB -= scoreboardWidth;
        }

        boolean hitBoundary = false;
        int renderTextWidth = (int)(renderScale * titleWidth);
        if (renderTextWidth > renderAreaWidthSB) {
            if (config.scoreboardMode == ScoreboardMode.FADE) {
                hideScoreboard = true;
                if (config.titleAlwaysFitsScreen) {
                    if (renderTextWidth > renderAreaWidth) {
                        renderScale = (float)renderAreaWidth / titleWidth;
                        hitBoundary = true;
                    }
                }
            } else {
                if (config.titleAlwaysFitsScreen) {
                    if (config.scoreboardMode == ScoreboardMode.MOVE) {
                        renderScale = (float)renderAreaWidthSB / titleWidth;
                    } else {
                        renderScale = (float)renderAreaWidth / titleWidth;
                    }
                    hitBoundary = true;
                }
            }
        }

        float titlePosX = (float)scaledWidth / 2;
        float titlePosY = (float)scaledHeight / 2;

        if (config.scoreboardMode == ScoreboardMode.MOVE) {
            titlePosX -= (float)scoreboardWidth / 2;
        }

        if (hitBoundary) {
            titlePosX += (float)(config.titleMarginLeft - config.titleMarginRight) / 2;
        }

        ri.posX = titlePosX;
        ri.posY = titlePosY;
        ri.scale = renderScale;
    }

    private void executeRenderInfo(MatrixStack matrices, float tickDelta) {
        if (renderTitle) {
            Profiler profiler = client.getProfiler();
            TextRenderer textRenderer = getFontRenderer();

            profiler.push("titleAndSubtitle");

            float ticksLeft = (float)titleTotalTicks - tickDelta;
            int alpha = 255;
            if (titleTotalTicks > titleFadeOutTicks + titleRemainTicks) {
                float r = (float)(titleFadeInTicks + titleRemainTicks + titleFadeOutTicks) - ticksLeft;
                alpha = (int)(r * 255.0F / titleFadeInTicks);
            }

            if (titleTotalTicks <= titleFadeOutTicks) {
                alpha = (int)(ticksLeft * 255.0F / titleFadeOutTicks);
            }

            alpha = MathHelper.clamp(alpha, 0, 255);
            if (alpha > 8) {
                matrices.push();
                matrices.translate(titleRI.posX, titleRI.posY, 0.0F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                matrices.push();
                matrices.scale(titleRI.scale, titleRI.scale, 1.0F);
                int titleColor = alpha << 24 & -0x1000000;
                int titleWidth = textRenderer.getWidth(titlec);
                drawTextBackground(matrices, textRenderer, -10, titleWidth, 0xFFFFFF | titleColor);
                textRenderer.drawWithShadow(matrices, titlec, (float)(-titleWidth / 2), -10.0F, 0xFFFFFF | titleColor);
                matrices.pop();

                if (subtitle != null) {
                    matrices.push();
                    matrices.scale(subtitleRI.scale, subtitleRI.scale, 1.0F);
                    int subtitleWidth = textRenderer.getWidth(subtitle);
                    drawTextBackground(matrices, textRenderer, 5, subtitleWidth, 0xFFFFFF | titleColor);
                    textRenderer.drawWithShadow(matrices, subtitle, (float)(-subtitleWidth / 2), 5.0F, 0xFFFFFF | titleColor);
                    matrices.pop();
                }

                RenderSystem.disableBlend();
                matrices.pop();
            }

            profiler.pop();
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    void tick_hook(CallbackInfo ci) {
        TitleFixerConfig config = TitleFixer.getConfig();
        if (hideScoreboard) {
            if (scoreboardOpacityGain > -255) {
                scoreboardOpacityGain -= config.scoreboardFadeSpeed;
                if (scoreboardOpacityGain < -255) {
                    scoreboardOpacityGain = -255;
                }
            }
        }
        else {
            if (scoreboardOpacityGain < 0) {
                scoreboardOpacityGain += config.scoreboardFadeSpeed;
                if (scoreboardOpacityGain > 0) {
                    scoreboardOpacityGain = 0;
                }
            }
        }
    }

    @ModifyArgs(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;fill(Lnet/minecraft/client/util/math/MatrixStack;IIIII)V"
        )
    )
    private void renderScoreboardSidebar_hook1(Args args) {
        if (scoreboardWidth == -1) {
            int x1 = args.get(1);
            int x2 = args.get(3);
            scoreboardWidth = x2 - x1;
        }
        int color = args.get(5);
        args.set(5, getNewScoreboardColor(color));
    }

    @Redirect(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/text/Text;FFI)I"
        )
    )
    private int renderScoreboardSidebar_hook2(TextRenderer textRenderer, MatrixStack matrices, Text text, float x, float y, int color) {
        int newColor = getNewScoreboardColor(color);
        int alpha = newColor >>> 24;
        if (alpha <= 8) {
            return 0;
        }
        return textRenderer.draw(matrices, text, x, y, newColor);
    }

    @Redirect(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/client/util/math/MatrixStack;Ljava/lang/String;FFI)I"
        )
    )
    private int renderScoreboardSidebar_hook3(TextRenderer textRenderer, MatrixStack matrices, String text, float x, float y, int color) {
        int newColor = getNewScoreboardColor(color);
        int alpha = newColor >>> 24;
        if (alpha <= 8) {
            return 0;
        }
        return textRenderer.draw(matrices, text, x, y, newColor);
    }

    private int getNewScoreboardColor(int color) {
        TitleFixerConfig config = TitleFixer.getConfig();
        int alpha = color >>> 24;
        alpha += scoreboardOpacityGain;
        if (alpha < config.scoreboardHideMinAlpha) {
            alpha = config.scoreboardHideMinAlpha;
        } else if (alpha > 255) {
            alpha = 255;
        }
        color &= ~0xFF000000;
        color |= alpha << 24;
        return color;
    }
}
