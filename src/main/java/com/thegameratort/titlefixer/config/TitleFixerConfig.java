package com.thegameratort.titlefixer.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "titlefixer")
public class TitleFixerConfig implements ConfigData {
    public boolean titleAlwaysFitsScreen = true;
    public ScoreboardMode scoreboardMode = ScoreboardMode.FADE;
    public int scoreboardFadeSpeed = 13;
    public int scoreboardHideMinAlpha = 0;
    public int titleMarginLeft = 8;
    public int titleMarginRight = 8;
    public float preferredTitleScale = 4.0F;
    public float preferredSubtitleScale = 2.0F;
}
