package com.thegameratort.titlefixer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thegameratort.titlefixer.TitleFixer;
import com.thegameratort.titlefixer.TitleRenderInfo;
import com.thegameratort.titlefixer.config.ScoreboardMode;
import com.thegameratort.titlefixer.config.TitleFixerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.ScoreboardObjective;
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
    @Shadow private int titleStayTicks;
    @Shadow private Text title;
    @Shadow private Text subtitle;
    @Shadow private int titleFadeInTicks;
    @Shadow private int titleRemainTicks;
    @Shadow private int titleFadeOutTicks;
    @Shadow private int scaledWidth;
    @Shadow private int scaledHeight;

    @Shadow public abstract TextRenderer getTextRenderer();
    @Shadow protected abstract void drawTextBackground(DrawContext context, TextRenderer textRenderer, int yOffset, int width, int color);

    private Text titlec;

    private int scoreboardWidth = -1;
    private int scoreboardOpacityGain = 0;

    public boolean renderTitle = false;
    public boolean hideScoreboard = false;
    public final TitleRenderInfo titleRI = new TitleRenderInfo();
    public final TitleRenderInfo subtitleRI = new TitleRenderInfo();

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"))
    private void preRenderHud(CallbackInfo ci) {
        scoreboardWidth = -1; // reset variable
        hideScoreboard = false; // reset variable
        titlec = title; // keep a reference for the title
        title = null; // prevent operation of the original title code
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("TAIL"))
    private void postRenderHud(DrawContext context, float tickDelta, CallbackInfo ci) {
        /* Calculate title stuff */
        collectRenderInfo();
        /* Render the title */
        executeRenderInfo(context, tickDelta);

        title = titlec; // restore the title
    }

    private void collectRenderInfo() {
        renderTitle = titlec != null && titleStayTicks > 0;
        if (renderTitle) {
            TitleFixerConfig config = TitleFixer.getConfig();
            TextRenderer textRenderer = getTextRenderer();

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

    private void executeRenderInfo(DrawContext context, float tickDelta) {
        if (renderTitle) {
            Profiler profiler = client.getProfiler();
            TextRenderer textRenderer = getTextRenderer();

            profiler.push("titleAndSubtitle");

            float ticksLeft = (float)titleStayTicks - tickDelta;
            int alpha = 255;
            if (titleStayTicks > titleFadeOutTicks + titleRemainTicks) {
                float r = (float)(titleFadeInTicks + titleRemainTicks + titleFadeOutTicks) - ticksLeft;
                alpha = (int)(r * 255.0F / titleFadeInTicks);
            }

            if (titleStayTicks <= titleFadeOutTicks) {
                alpha = (int)(ticksLeft * 255.0F / titleFadeOutTicks);
            }

            alpha = MathHelper.clamp(alpha, 0, 255);
            if (alpha > 8) {
                MatrixStack matrices = context.getMatrices();
                matrices.push();
                matrices.translate(titleRI.posX, titleRI.posY, 0.0F);
                RenderSystem.enableBlend();
                matrices.push();
                matrices.scale(titleRI.scale, titleRI.scale, 1.0F);
                int titleColor = alpha << 24 & -0x1000000;
                int titleWidth = textRenderer.getWidth(titlec);
                drawTextBackground(context, textRenderer, -10, titleWidth, 0xFFFFFF | titleColor);
                context.drawTextWithShadow(textRenderer, titlec, -titleWidth / 2, -10, 0xFFFFFF | titleColor);
                matrices.pop();

                if (subtitle != null) {
                    matrices.push();
                    matrices.scale(subtitleRI.scale, subtitleRI.scale, 1.0F);
                    int subtitleWidth = textRenderer.getWidth(subtitle);
                    drawTextBackground(context, textRenderer, 5, subtitleWidth, 0xFFFFFF | titleColor);
                    context.drawTextWithShadow(textRenderer, subtitle, -subtitleWidth / 2, 5, 0xFFFFFF | titleColor);
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

    @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void renderScoreboardSidebar_hook0(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (getNewScoreboardColor(-1) >>> 24 <= 8) {
            ci.cancel();
        }
    }

    @ModifyArgs(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"
        )
    )
    private void renderScoreboardSidebar_hook1(Args args) {
        if (scoreboardWidth == -1) {
            int x1 = args.get(0);
            int x2 = args.get(2);
            scoreboardWidth = x2 - x1;
        }
        args.set(4, getNewScoreboardColor(args.get(4)));
    }

    @ModifyArg(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)I"
        ), index = 4
    )
    private int renderScoreboardSidebar_hook2(int color) {
        return getNewScoreboardColor(color);
    }

    @ModifyArg(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I"
        ), index = 4
    )
    private int renderScoreboardSidebar_hook3(int color) {
        return getNewScoreboardColor(color);
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
