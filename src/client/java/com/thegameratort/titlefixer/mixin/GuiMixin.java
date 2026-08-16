package com.thegameratort.titlefixer.mixin;

import com.thegameratort.titlefixer.TitleFixer;
import com.thegameratort.titlefixer.TitleRenderInfo;
import com.thegameratort.titlefixer.config.ScoreboardMode;
import com.thegameratort.titlefixer.config.TitleFixerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.UnknownNullability;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow private int titleStayTime;
    @Shadow private Component title;
    @Shadow private Component subtitle;
    @Shadow private int titleFadeInTime;
    @Shadow private int titleTime;
    @Shadow private int titleFadeOutTime;

    @Shadow public abstract Font getFont();

    @Unique private Component titlec;

    @Unique private int scoreboardWidth = -1;
    @Unique private int scoreboardOpacityGain = 0;

    @Unique public boolean renderTitle = false;
    @Unique public boolean hideScoreboard = false;
    @Unique public final TitleRenderInfo titleRI = new TitleRenderInfo();
    @Unique public final TitleRenderInfo subtitleRI = new TitleRenderInfo();

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void preRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        scoreboardWidth = -1; // reset variable
        hideScoreboard = false; // reset variable
        titlec = title; // keep a reference for the title
        title = null; // prevent operation of the original title code

        /* Calculate title stuff */
        collectRenderInfo(context);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void postRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        title = titlec; // restore the title
    }

    @Unique
    private void collectRenderInfo(GuiGraphicsExtractor context) {
        renderTitle = titlec != null && titleTime > 0;
        if (renderTitle) {
            TitleFixerConfig config = TitleFixer.getConfig();
            Font textRenderer = getFont();

            int titleWidth = textRenderer.width(titlec);
            collectTitleRenderInfo(context, titleRI, config.preferredTitleScale, titleWidth, config);

            if (subtitle != null) {
                int subtitleWidth = textRenderer.width(subtitle);
                collectTitleRenderInfo(context, subtitleRI, config.preferredSubtitleScale, subtitleWidth, config);
            }
        }
    }

    @Unique
    private void collectTitleRenderInfo(@UnknownNullability GuiGraphicsExtractor context, TitleRenderInfo ri, float titleScale, int titleWidth, TitleFixerConfig config) {
        int scaledWidth = context.guiWidth();
        int scaledHeight = context.guiHeight();

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

    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void renderTitleAndSubtitle_hook(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (renderTitle) {
            ProfilerFiller profiler = Profiler.get();
            Font textRenderer = getFont();

            profiler.push("titleAndSubtitle");

            float ticksLeft = (float) titleTime - tickCounter.getGameTimeDeltaPartialTick(false);
            int alpha = 255;
            if (titleTime > titleFadeOutTime + titleStayTime) {
                float r = (float)(titleFadeInTime + titleStayTime + titleFadeOutTime) - ticksLeft;
                alpha = (int)(r * 255.0F / titleFadeInTime);
            }

            if (titleTime <= titleFadeOutTime) {
                alpha = (int)(ticksLeft * 255.0F / titleFadeOutTime);
            }

            alpha = Mth.clamp(alpha, 0, 255);
            if (alpha > 8) {
                Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.translate(titleRI.posX, titleRI.posY);
                matrices.pushMatrix();
                matrices.scale(titleRI.scale, titleRI.scale);
                int titleColor = ARGB.color(alpha, -1);
                int titleWidth = textRenderer.width(titlec);
                context.textWithBackdrop(textRenderer, titlec, -titleWidth / 2, -10, titleWidth, titleColor);
                matrices.popMatrix();

                if (subtitle != null) {
                    matrices.pushMatrix();
                    matrices.scale(subtitleRI.scale, subtitleRI.scale);
                    int subtitleWidth = textRenderer.width(subtitle);
                    context.textWithBackdrop(textRenderer, subtitle, -subtitleWidth / 2, 5, subtitleWidth, titleColor);
                    matrices.popMatrix();
                }

                matrices.popMatrix();
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

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V", at = @At("HEAD"), cancellable = true)
    private void renderScoreboardSidebar_hook0(GuiGraphicsExtractor context, Objective objective, CallbackInfo ci) {
        if (getNewScoreboardColor(-1) >>> 24 <= 8) {
            ci.cancel();
        }
    }

    @ModifyArgs(
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
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
            method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
            ), index = 4
    )
    private int renderScoreboardSidebar_hook2(int color) {
        return getNewScoreboardColor(color);
    }

    @Unique
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
