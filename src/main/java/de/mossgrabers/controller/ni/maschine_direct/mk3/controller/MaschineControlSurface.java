// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.controller;

import de.mossgrabers.controller.ni.maschine_direct.MaschineDirect;
import de.mossgrabers.controller.ni.maschine_direct.core.AbstractMaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.core.controller.MaschinePadGrid;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.MidiConstants;


/**
 * The NI Maschine control surface.
 *
 * @author Jürgen Moßgraber
 */
@SuppressWarnings("javadoc")
public class MaschineControlSurface extends AbstractMaschineControlSurface<MaschineConfiguration>
{
	private final MaschineUsb usbInterface;

	//
	// "MIDI CCs"; these are used only internally, and don't actually bubble up to the DAW --
	// but using the same CCs as the Maschine uses in non-direct mode allows code sharing.
	//

    public static final int TOUCHSTRIP        = 1;
    public static final int TOUCHSTRIP_TOUCH  = 2;

    public static final int FOOTSWITCH1_TIP   = 3;
    public static final int FOOTSWITCH1_RING  = 4;
    public static final int FOOTSWITCH2_TIP   = 5;
    public static final int FOOTSWITCH2_RING  = 6;

    public static final int ENCODER           = 7;
    public static final int ENCODER_PUSH      = 8;
    public static final int ENCODER_TOUCH     = 9;

    /** Mode buttons are from CC 22 to 29. */
    public static final int MODE_BUTTON_1     = 22;
    public static final int MODE_BUTTON_2     = 23;
    public static final int MODE_BUTTON_3     = 24;
    public static final int MODE_BUTTON_4     = 25;
    public static final int MODE_BUTTON_5     = 26;
    public static final int MODE_BUTTON_6     = 27;
    public static final int MODE_BUTTON_7     = 28;
    public static final int MODE_BUTTON_8     = 29;

    public static final int CURSOR_UP         = 30;
    public static final int CURSOR_RIGHT      = 31;
    public static final int CURSOR_DOWN       = 32;
    public static final int CURSOR_LEFT       = 33;

    public static final int GROUP             = 34;
    public static final int AUTO              = 35;
    public static final int LOCK              = 36;
    public static final int NOTE_REPEAT       = 37;

    public static final int PROJECT           = 38;
    public static final int FAVORITES         = 39;
    public static final int BROWSER           = 40;

    public static final int CHANNEL           = 41;
    public static final int ARRANGER          = 42;
    public static final int MIXER             = 43;

    public static final int VOLUME            = 44;
    public static final int PLUGIN            = 45;
    public static final int SWING             = 46;
    public static final int SAMPLING          = 47;
    public static final int TEMPO             = 48;

    public static final int PITCH             = 49;
    public static final int MOD               = 50;
    public static final int PERFORM           = 51;
    public static final int NOTES             = 52;

    public static final int RESTART           = 53;
    public static final int ERASE             = 54;
    public static final int TAP_METRO         = 55;
    public static final int FOLLOW            = 56;
    public static final int PLAY              = 57;
    public static final int REC               = 58;
    public static final int STOP              = 59;

    /** Mode button touch events are from CC 60 to 67. */
    public static final int MODE_KNOB_TOUCH_1 = 60;

    /** Mode buttons are from CC 70 to 77. */
    public static final int MODE_KNOB_1       = 70;

    public static final int FIXED_VEL         = 80;
    public static final int PAD_MODE          = 81;
    public static final int KEYBOARD          = 82;
    public static final int CHORDS            = 84;
    public static final int STEP              = 83;
    public static final int SCENE             = 85;
    public static final int PATTERN           = 86;
    public static final int EVENTS            = 87;
    public static final int VARIATION         = 88;
    public static final int DUPLICATE         = 89;
    public static final int SELECT            = 90;
    public static final int SOLO              = 91;
    public static final int MUTE              = 92;
    public static final int SHIFT             = 93;

    /** Banks are from CC 100 to 107. */
    public static final int BANK_1            = 100;

    public static final int PAGE_LEFT         = 110;
    public static final int PAGE_RIGHT        = 111;





    /**
     * Constructor.
     *
     * @param host The host
     * @param colorManager The color manager
     * @param maschine The maschine description
     * @param configuration The configuration
	 * @param usbInterface The MaschineUsb interface for communicating with the Maschine directly.
     */
    public MaschineControlSurface (final IHost host, final ColorManager colorManager, final MaschineDirect maschine, final MaschineConfiguration configuration, final MaschineUsb usbInterface, final IMidiInput input)
    {
        super (host, configuration, colorManager, maschine, null, input, new MaschinePadGrid (colorManager, usbInterface, host), maschine.getWidth (), maschine.getHeight ());
		this.usbInterface = usbInterface;
    }


	/**
	 * Returns the USB wrapper for this device.
	 */ 
	public MaschineUsb getUsb() {
		return this.usbInterface;
	}


    /** {@inheritDoc} */
    @Override
    protected void flushHardware ()
    {
        super.flushHardware ();
        ((MaschinePadGrid) this.padGrid).flush ();
    }

	@Override
	protected void internalShutdown() {
		super.internalShutdown();
		this.usbInterface.shutdown();
	}


    /**
     * Set the display value of the ribbon on the controller.
     *
     * @param value The value to set
     */
    public void setRibbonValue (final int value)
    {
		// TODO: implement
    }
}
