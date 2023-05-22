// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine.mk3;

import de.mossgrabers.controller.ni.maschine.Maschine;
import de.mossgrabers.controller.ni.maschine.core.RibbonMode;
import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IColorSetting;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.IIntegerSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.configuration.IStringSetting;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.graphics.IGraphicsConfiguration;
import de.mossgrabers.framework.scale.ScaleLayout;

import java.util.List;


/**
 * The configuration settings for Maschine.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineConfiguration extends AbstractConfiguration implements IGraphicsConfiguration
{
	//
	// Constants.
	//

    /** Background color of an element. */
    public static final Integer     COLOR_BACKGROUND                = Integer.valueOf (70);
    /** Border color of an element. */
    public static final Integer     COLOR_BORDER                    = Integer.valueOf (71);
    /** Text color of an element. */
    public static final Integer     COLOR_TEXT                      = Integer.valueOf (72);
    /** Fader color of an element. */
    public static final Integer     COLOR_FADER                     = Integer.valueOf (73);
    /** VU color of an element. */
    public static final Integer     COLOR_VU                        = Integer.valueOf (74);
    /** Edit color of an element. */
    public static final Integer     COLOR_EDIT                      = Integer.valueOf (75);
    /** Record color of an element. */
    public static final Integer     COLOR_RECORD                    = Integer.valueOf (76);
    /** Solo color of an element. */
    public static final Integer     COLOR_SOLO                      = Integer.valueOf (77);
    /** Mute color of an element. */
    public static final Integer     COLOR_MUTE                      = Integer.valueOf (78);
    /** Background color darker of an element. */
    public static final Integer     COLOR_BACKGROUND_DARKER         = Integer.valueOf (79);
    /** Background color lighter of an element. */
    public static final Integer     COLOR_BACKGROUND_LIGHTER        = Integer.valueOf (80);

	/** Category for configuring display colors. */
    private static final String     CATEGORY_COLORS                 = "Display Colors";

	//
	// State.
	//

    /** Setting for the ribbon mode. */
    public static final Integer RIBBON_MODE = Integer.valueOf (50);

    private final Maschine      maschine;

    /** What does the ribbon send? **/
    private RibbonMode          ribbonMode  = RibbonMode.PITCH_DOWN;

    private IEnumSetting        ribbonModeSetting;


    private int             displayBrightness           = 255;
    private int             ledBrightness               = 127;
	private String          serialForDisplay            = null;
    private ColorEx         colorBackground             = DEFAULT_COLOR_BACKGROUND;
    private ColorEx         colorBorder                 = DEFAULT_COLOR_BORDER;
    private ColorEx         colorText                   = DEFAULT_COLOR_TEXT;
    private ColorEx         colorFader                  = DEFAULT_COLOR_FADER;
    private ColorEx         colorVU                     = DEFAULT_COLOR_VU;
    private ColorEx         colorEdit                   = DEFAULT_COLOR_EDIT;
    private ColorEx         colorRecord                 = DEFAULT_COLOR_RECORD;
    private ColorEx         colorSolo                   = DEFAULT_COLOR_SOLO;
    private ColorEx         colorMute                   = DEFAULT_COLOR_MUTE;
    private ColorEx         colorBackgroundDarker       = DEFAULT_COLOR_BACKGROUND_DARKER;
    private ColorEx         colorBackgroundLighter      = DEFAULT_COLOR_BACKGROUND_LIGHTER;

    private final boolean   hasDisplay;

    private IIntegerSetting displayBrightnessSetting;
    private IIntegerSetting ledBrightnessSetting;
    private IIntegerSetting ribbonModeCCSetting;
    private IEnumSetting    ribbonModeNoteRepeatSetting;
    private IEnumSetting    velocityCurveSetting;
    private IEnumSetting    padThresholdSetting;
    private IEnumSetting    debugModeSetting;
    private IColorSetting   colorBackgroundSetting;
    private IColorSetting   colorBackgroundDarkerSetting;
    private IColorSetting   colorBackgroundLighterSetting;
    private IColorSetting   colorBorderSetting;
    private IColorSetting   colorTextSetting;
    private IColorSetting   colorFaderSetting;
    private IColorSetting   colorVUSetting;
    private IColorSetting   colorEditSetting;
    private IColorSetting   colorRecordSetting;
    private IColorSetting   colorSoloSetting;
    private IColorSetting   colorMuteSetting;
    private IEnumSetting    sessionViewSetting;
    private IEnumSetting    displayScenesClipsSetting;
	private IStringSetting  serialForDisplaySetting;


    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param valueChanger The value changer
     * @param arpeggiatorModes The available arpeggiator modes
     * @param maschine The type of Maschine
     */
    public MaschineConfiguration (final IHost host, final IValueChanger valueChanger, final List<ArpeggiatorMode> arpeggiatorModes, final Maschine maschine)
    {
        super (host, valueChanger, arpeggiatorModes);

        this.maschine = maschine;

		// FIXME(ktemkin): choose this based on our model
		this.hasDisplay = true;
    }


    /** {@inheritDoc} */
    @Override
    public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
    {
        ///////////////////////////
        // Scale

        this.activateScaleSetting (documentSettings);
        this.activateScaleBaseSetting (documentSettings);
        this.activateScaleInScaleSetting (documentSettings);
        this.activateScaleLayoutSetting (documentSettings, ScaleLayout.SEQUENT_UP.getName ());

        ///////////////////////////
        // Note Repeat

        this.activateNoteRepeatSetting (documentSettings);

        ///////////////////////////
        // Transport

        this.activateRecordButtonSetting (globalSettings);
        this.activateShiftedRecordButtonSetting (globalSettings);
        this.activateBehaviourOnPauseSetting (globalSettings);

        ///////////////////////////
        // Play and Sequence

        this.activateAccentActiveSetting (globalSettings);
        this.activateAccentValueSetting (globalSettings);
        this.activateQuantizeAmountSetting (globalSettings);

        final String [] ribbonModeNames = RibbonMode.getNames ();
        this.ribbonModeSetting = globalSettings.getEnumSetting ("Ribbon Mode", CATEGORY_PLAY_AND_SEQUENCE, ribbonModeNames, ribbonModeNames[0]);
        this.ribbonModeSetting.addValueObserver (value -> {
            this.ribbonMode = RibbonMode.lookupByName (value);
            this.notifyObservers (RIBBON_MODE);
        });
        this.isSettingActive.add (RIBBON_MODE);

        ///////////////////////////
        // Session

        this.activateSelectClipOnLaunchSetting (globalSettings);
        this.activateActionForRecArmedPad (globalSettings);

        ///////////////////////////
        // Workflow

        this.activateExcludeDeactivatedItemsSetting (globalSettings);
        this.activateNewClipLengthSetting (globalSettings);
        this.activateKnobSpeedSetting (globalSettings);

        final int footswitches = this.maschine.getFootswitches ();
        if (footswitches >= 2)
        {
            this.activateFootswitchSetting (globalSettings, 0, "Footswitch (Tip)");
            this.activateFootswitchSetting (globalSettings, 1, "Footswitch (Ring)");

            if (footswitches == 4)
            {
                this.activateFootswitchSetting (globalSettings, 2, "Footswitch 2 (Tip)");
                this.activateFootswitchSetting (globalSettings, 3, "Footswitch 2 (Ring)");
            }
        }

        ///////////////////////////
        // Pads

        this.activateConvertAftertouchSetting (globalSettings);

        ///////////////////////////
        // Display colors
        this.activateDisplayColorSettings (globalSettings);


        ///////////////////////////
        // Display.
		this.serialForDisplaySetting = globalSettings.getStringSetting("Serial Number (for display)", "Misc", 8, "");
		this.serialForDisplaySetting.addValueObserver(serial -> {
			this.serialForDisplay = serial;
		});

    }


    /**
     * Activate the color settings for the NI-style display.
     *
     * @param settingsUI The settings
     */
    private void activateDisplayColorSettings (final ISettingsUI settingsUI)
    {
		if (!this.hasDisplay) {
			return;
		}

        settingsUI.getSignalSetting ("Reset colors to default", CATEGORY_COLORS, "Reset").addSignalObserver (value -> {
            this.colorBackgroundSetting.set (DEFAULT_COLOR_BACKGROUND);
            this.colorBackgroundDarkerSetting.set (DEFAULT_COLOR_BACKGROUND_DARKER);
            this.colorBackgroundLighterSetting.set (DEFAULT_COLOR_BACKGROUND_LIGHTER);
            this.colorBorderSetting.set (DEFAULT_COLOR_BORDER);
            this.colorTextSetting.set (DEFAULT_COLOR_TEXT);
            this.colorFaderSetting.set (DEFAULT_COLOR_FADER);
            this.colorVUSetting.set (DEFAULT_COLOR_VU);
            this.colorEditSetting.set (DEFAULT_COLOR_EDIT);
            this.colorRecordSetting.set (DEFAULT_COLOR_RECORD);
            this.colorSoloSetting.set (DEFAULT_COLOR_SOLO);
            this.colorMuteSetting.set (DEFAULT_COLOR_MUTE);
        });

        this.colorBackgroundSetting = settingsUI.getColorSetting ("Background", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND);
        this.colorBackgroundSetting.addValueObserver (color -> {
            this.colorBackground = color;
            this.notifyObservers (COLOR_BACKGROUND);
        });

        this.colorBackgroundDarkerSetting = settingsUI.getColorSetting ("Background Darker", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND_DARKER);
        this.colorBackgroundDarkerSetting.addValueObserver (color -> {
            this.colorBackgroundDarker = color;
            this.notifyObservers (COLOR_BACKGROUND_DARKER);
        });

        this.colorBackgroundLighterSetting = settingsUI.getColorSetting ("Background Selected", CATEGORY_COLORS, DEFAULT_COLOR_BACKGROUND_LIGHTER);
        this.colorBackgroundLighterSetting.addValueObserver (color -> {
            this.colorBackgroundLighter = color;
            this.notifyObservers (COLOR_BACKGROUND_LIGHTER);
        });

        this.colorBorderSetting = settingsUI.getColorSetting ("Border", CATEGORY_COLORS, DEFAULT_COLOR_BORDER);
        this.colorBorderSetting.addValueObserver (color -> {
            this.colorBorder = color;
            this.notifyObservers (COLOR_BORDER);
        });

        this.colorTextSetting = settingsUI.getColorSetting ("Text", CATEGORY_COLORS, DEFAULT_COLOR_TEXT);
        this.colorTextSetting.addValueObserver (color -> {
            this.colorText = color;
            this.notifyObservers (COLOR_TEXT);
        });

        this.colorFaderSetting = settingsUI.getColorSetting ("Fader", CATEGORY_COLORS, DEFAULT_COLOR_FADER);
        this.colorFaderSetting.addValueObserver (color -> {
            this.colorFader = color;
            this.notifyObservers (COLOR_FADER);
        });

        this.colorVUSetting = settingsUI.getColorSetting ("VU", CATEGORY_COLORS, DEFAULT_COLOR_VU);
        this.colorVUSetting.addValueObserver (color -> {
            this.colorVU = color;
            this.notifyObservers (COLOR_VU);
        });

        this.colorEditSetting = settingsUI.getColorSetting ("Edit", CATEGORY_COLORS, DEFAULT_COLOR_EDIT);
        this.colorEditSetting.addValueObserver (color -> {
            this.colorEdit = color;
            this.notifyObservers (COLOR_EDIT);
        });

        this.colorRecordSetting = settingsUI.getColorSetting ("Record", CATEGORY_COLORS, DEFAULT_COLOR_RECORD);
        this.colorRecordSetting.addValueObserver (color -> {
            this.colorRecord = color;
            this.notifyObservers (COLOR_RECORD);
        });

        this.colorSoloSetting = settingsUI.getColorSetting ("Solo", CATEGORY_COLORS, DEFAULT_COLOR_SOLO);
        this.colorSoloSetting.addValueObserver (color -> {
            this.colorSolo = color;
            this.notifyObservers (COLOR_SOLO);
        });

        this.colorMuteSetting = settingsUI.getColorSetting ("Mute", CATEGORY_COLORS, DEFAULT_COLOR_MUTE);
        this.colorMuteSetting.addValueObserver (color -> {
            this.colorMute = color;
            this.notifyObservers (COLOR_MUTE);
        });
    }


    /**
     * Set the ribbon mode.
     *
     * @param mode The functionality for the ribbon
     */
    public void setRibbonMode (final RibbonMode mode)
    {
        this.ribbonModeSetting.set (mode.getName ());
    }


    /**
     * Get the ribbon mode.
     *
     * @return The functionality for the ribbon
     */
    public RibbonMode getRibbonMode ()
    {
        return this.ribbonMode;
    }

	
	//
	// Graphics configuration methods.
	//

    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackground ()
    {
        return this.colorBackground;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundDarker ()
    {
        return this.colorBackgroundDarker;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBackgroundLighter ()
    {
        return this.colorBackgroundLighter;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorBorder ()
    {
        return this.colorBorder;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorText ()
    {
        return this.colorText;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorEdit ()
    {
        return this.colorEdit;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorFader ()
    {
        return this.colorFader;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorVu ()
    {
        return this.colorVU;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorRecord ()
    {
        return this.colorRecord;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorSolo ()
    {
        return this.colorSolo;
    }


    /** {@inheritDoc} */
    @Override
    public ColorEx getColorMute ()
    {
        return this.colorMute;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isAntialiasEnabled ()
    {
        return true;
    }

	/** Returns the serial number the user wants to use to display relevant data. */
	public String getSerialForDisplay() {
		return this.serialForDisplay;
	}

}
