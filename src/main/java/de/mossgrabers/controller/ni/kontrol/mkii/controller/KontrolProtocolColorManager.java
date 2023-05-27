// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.kontrol.mkii.controller;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;


/**
 * Different colors to use for the buttons of Komplete Kontrol MkII.
 *
 * @author Jürgen Moßgraber
 */
public class KontrolProtocolColorManager extends ColorManager
{
	public static final int           COLOR_BLACK        = 0;
	public static final int           COLOR_DARK_GREY    = 76;
	public static final int           COLOR_GREY         = 77;
	public static final int           COLOR_WHITE        = 78;
	public static final int           COLOR_ROSE         = 7;
	public static final int           COLOR_RED          = 6;
	public static final int           COLOR_RED_LO       = 5;
	public static final int           COLOR_AMBER        = 14;
	public static final int           COLOR_AMBER_LO     = 13;
	public static final int           COLOR_LIME         = 34;
	public static final int           COLOR_LIME_LO      = 33;
	public static final int           COLOR_GREEN        = 30;
	public static final int           COLOR_GREEN_LO     = 29;
	public static final int           COLOR_SPRING       = 26;
	public static final int           COLOR_SPRING_LO    = 25;
	public static final int           COLOR_TURQUOISE_LO = 27;
	public static final int           COLOR_TURQUOISE    = 31;
	public static final int           COLOR_SKY          = 38;
	public static final int           COLOR_SKY_LO       = 37;
	public static final int           COLOR_BLUE         = 42;
	public static final int           COLOR_BLUE_LO      = 45;
	public static final int           COLOR_MAGENTA      = 58;
	public static final int           COLOR_MAGENTA_LO   = 57;
	public static final int           COLOR_PINK         = 62;
	public static final int           COLOR_PINK_LO      = 61;
	public static final int           COLOR_ORANGE       = 10;
	public static final int           COLOR_ORANGE_LO    = 9;
	public static final int           COLOR_PURPLE       = 50;
	public static final int           COLOR_PURPLE_LO    = 53;
	public static final int           COLOR_SKIN         = 11;
	public static final int           COLOR_YELLOW_LO    = 21;
	public static final int           COLOR_YELLOW       = 22;


    /**
     * Constructor.
     */
    public KontrolProtocolColorManager ()
    {
        this.registerColorIndex (AbstractFeatureGroup.BUTTON_COLOR_OFF, 0);
        this.registerColorIndex (AbstractFeatureGroup.BUTTON_COLOR_ON, 0);
        this.registerColorIndex (AbstractMode.BUTTON_COLOR_HI, 1);

        this.registerColorIndex (ColorManager.BUTTON_STATE_OFF, 0);
        this.registerColorIndex (ColorManager.BUTTON_STATE_ON, 0);
        this.registerColorIndex (ColorManager.BUTTON_STATE_HI, 1);
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColor (final int colorIndex, final ButtonID buttonID)
    {
        if (buttonID == null)
            return ColorEx.BLACK;

        switch (buttonID)
        {
            case PLAY:
                return colorIndex > 0 ? ColorEx.GREEN : ColorEx.DARK_GREEN;
            case RECORD:
            case REC_ARM:
            case ROW3_1:
            case ROW3_2:
            case ROW3_3:
            case ROW3_4:
            case ROW3_5:
            case ROW3_6:
            case ROW3_7:
            case ROW3_8:
                return colorIndex > 0 ? ColorEx.RED : ColorEx.DARK_RED;
            case SOLO:
            case ROW2_1:
            case ROW2_2:
            case ROW2_3:
            case ROW2_4:
            case ROW2_5:
            case ROW2_6:
            case ROW2_7:
            case ROW2_8:
                return colorIndex > 0 ? ColorEx.BLUE : ColorEx.DARK_BLUE;
            case MUTE:
            case ROW1_1:
            case ROW1_2:
            case ROW1_3:
            case ROW1_4:
            case ROW1_5:
            case ROW1_6:
            case ROW1_7:
            case ROW1_8:
                return colorIndex > 0 ? ColorEx.ORANGE : ColorEx.DARK_ORANGE;
            case ROW_SELECT_1:
            case ROW_SELECT_2:
            case ROW_SELECT_3:
            case ROW_SELECT_4:
            case ROW_SELECT_5:
            case ROW_SELECT_6:
            case ROW_SELECT_7:
            case ROW_SELECT_8:
                return colorIndex > 0 ? ColorEx.GRAY : ColorEx.BLACK;
            default:
                return colorIndex > 0 ? ColorEx.WHITE : ColorEx.DARK_GRAY;
        }
    }
}
