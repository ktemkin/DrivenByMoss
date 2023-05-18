// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3;

import de.mossgrabers.controller.ni.maschine_direct.MaschineDirect;
import de.mossgrabers.controller.ni.maschine_direct.core.RibbonMode;
import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.color.ColorEx;
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
	/** Graphics settings. */
	// FIXME(ktemkin): make these configurable
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

    /** Setting for the ribbon mode. */
    public static final Integer RIBBON_MODE = Integer.valueOf (50);

    private final MaschineDirect      maschine;

    /** What does the ribbon send? **/
    private RibbonMode          ribbonMode  = RibbonMode.PITCH_DOWN;

    private IEnumSetting        ribbonModeSetting;


    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param valueChanger The value changer
     * @param arpeggiatorModes The available arpeggiator modes
     * @param maschine The type of Maschine
     */
    public MaschineConfiguration (final IHost host, final IValueChanger valueChanger, final List<ArpeggiatorMode> arpeggiatorModes, final MaschineDirect maschine)
    {
        super (host, valueChanger, arpeggiatorModes);

        this.maschine = maschine;
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
	// GFX
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


	//
	// FIXME(ktemkin): implement these
	// 

	public boolean isMuteLongPressed() {
		return false;
	}

	public boolean isMuteSoloLocked() {
		return false;
	}

	public boolean isMuteState() {
		return false;
	}

	public boolean isSoloLongPressed() {
		return false;
	}

	public boolean isSoloState() {
		return false;
	}


}
