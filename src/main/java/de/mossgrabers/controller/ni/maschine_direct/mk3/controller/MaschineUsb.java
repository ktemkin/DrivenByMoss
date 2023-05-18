// Written by Jürgen Moßgraber - mossgrabers.de
// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.controller;

import de.mossgrabers.controller.ni.maschine_direct.core.controller.IMaschineDirectConnection;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.core.command.IButtonEventCallback;
import de.mossgrabers.controller.ni.maschine_direct.core.command.IContinuousChangeCallback;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IMemoryBlock;
import de.mossgrabers.framework.daw.midi.IMidiAccess;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.usb.IUsbDevice;
import de.mossgrabers.framework.usb.IHidDevice;
import de.mossgrabers.framework.usb.IUsbEndpoint;
import de.mossgrabers.framework.usb.UsbException;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;

import java.nio.ByteBuffer;
import java.lang.Math;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

import purejavahidapi.PureJavaHidApi;


/**
 * Connects directly to a Maschine Mk3 (or equivalent).
 *
 * @author Jürgen Moßgraber and Kate Temkin
 */
public class MaschineUsb implements IMaschineDirectConnection
{
	//
	// General constants.
	//

	/** The maximum size of a HID _output_ report. */
	private static final int               MAX_OUTPUT_REPORT_SZ  = 64;

    /** The size of the display content. 2Bpp on a 480 * 272 display. */
    private static final int               DISPLAY_DATA_SZ       = 480 * 272 * 2;
	private static final int               DISPLAY_PACKET_SIZE   = 261156;
    private static final int               TIMEOUT               = 10000;

    private static final byte []           DISPLAY_HEADER_LEFT   =
    {
        (byte) 0x84, // Command
        (byte) 0x00,
        (byte) 0x00, // Screen number. 
        (byte) 0x60, // ???
        (byte) 0x00,            
        (byte) 0x00,            
        (byte) 0x00,            
        (byte) 0x00,            
		(byte) 0x00, // X position, MSB
		(byte) 0x00, // X position, LSB
		(byte) 0x00, // Y position, MSB
		(byte) 0x00, // Y position, LSB
		(byte) 0x01, // Width (480 for a full update), MSB
		(byte) 0xe0, // Width, LSB
		(byte) 0x01, // Height (272 for a full update), MSB
		(byte) 0x10, // Height, LSB
		(byte) 0x02, // ???
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0xFF, // Half the image size in _pixels_, MSB
		(byte) 0x00, // Half the image size in _pixels_, LSB
    };

    private static final byte []           DISPLAY_HEADER_RIGHT   =
    {
        (byte) 0x84, // Command
        (byte) 0x00,
        (byte) 0x01, // Screen number. 
        (byte) 0x60, // ???
        (byte) 0x00,            
        (byte) 0x00,            
        (byte) 0x00,            
        (byte) 0x00,            
		(byte) 0x00, // X position, MSB
		(byte) 0x00, // X position, LSB
		(byte) 0x00, // Y position, MSB
		(byte) 0x00, // Y position, LSB
		(byte) 0x01, // Width (480 for a full update), MSB
		(byte) 0xe0, // Width, LSB
		(byte) 0x01, // Height (272 for a full update), MSB
		(byte) 0x10, // Height, LSB
		(byte) 0x02, // ???
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0x00,
		(byte) 0xFF, // Half the image size, MSB
		(byte) 0x00, // Half the image size, LSB
    };

    private static final byte []           DISPLAY_FOOTER   =
    {
        (byte) 0x02, // ???
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x03, // ???
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x40, // ???
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x00,
    };

	//
	// Internal state.
	//

    private IUsbDevice                     usbDevice;
    private IUsbEndpoint                   usbDisplayEndpoint;
	private final IHidDevice               hidDevice;

    private MaschineControlSurface         surface;
    private final IModel                   model;
    private final IMidiInput               input;
    private final MaschineConfiguration    configuration;

    private final IMemoryBlock             displayBlockLeft;
    private final IMemoryBlock             displayBlockRight;
	private final IMemoryBlock             reportOutputBlock;

    private final byte []                  byteStoreLeft    = new byte [DISPLAY_PACKET_SIZE];
    private final byte []                  byteStoreRight   = new byte [DISPLAY_PACKET_SIZE];
    private final byte []                  byteStoreReport  = new byte [MAX_OUTPUT_REPORT_SZ];

    private final Object                   sendLock               = new Object ();
    private final Object                   screenBufferUpdateLock = new Object ();
    private final Object                   hidBufferUpdateLock    = new Object ();

    private final ScheduledExecutorService sendExecutor           = Executors.newSingleThreadScheduledExecutor ();


	/** An incoming report with updates to the digital inputs. */
	private final byte REPORT_DIGITAL_UPDATE = 0x01;

	/** An incoming report with updates to the analog inputs. */
	private final byte REPORT_ANALOG_UPDATE = 0x02;

	/** The HID output report used to set button brightnesses. */
	private final byte REPORT_UPDATE_BUTTONS = (byte)0x80;

	/** The HID output report used to set the pad color. */
	private final byte REPORT_UPDATE_PADS = (byte)0x81;


	/** Mappings of the button bytes in the lowest HID descriptors to their stand-in DAW button-IDs. */
	private final ButtonID [][] BUTTON_INPUT_MAPPINGS = {
		/* 0 */ { ButtonID.ROW1_8, ButtonID.SHIFT, ButtonID.LEFT, ButtonID.DOWN, ButtonID.RIGHT, ButtonID.UP, null, ButtonID.ENTER },
		/* 1 */ { ButtonID.TRACK_SELECT_8, ButtonID.TRACK_SELECT_7, ButtonID.TRACK_SELECT_6, ButtonID.TRACK_SELECT_5, ButtonID.TRACK_SELECT_4, ButtonID.TRACK_SELECT_3, ButtonID.TRACK_SELECT_2, ButtonID.TRACK_SELECT_1 },
		/* 2 */ { ButtonID.FOOTSWITCH1, null, ButtonID.OVERDUB, ButtonID.REPEAT, ButtonID.TEMPO_TOUCH, ButtonID.SWING, ButtonID.VOLUME, ButtonID.F4 },
		/* 3 */ { ButtonID.NOTE, ButtonID.CLIP, ButtonID.SCENE1, ButtonID.ACCENT, ButtonID.ROW2_4, ButtonID.ROW2_3, ButtonID.ROW2_2, ButtonID.ROW2_1},
		/* 4 */ { ButtonID.F2, ButtonID.F1, ButtonID.MUTE, ButtonID.SOLO, ButtonID.SELECT, ButtonID.DUPLICATE, ButtonID.TOGGLE_DEVICE, null },
		/* 5 */ { ButtonID.STOP, ButtonID.RECORD, ButtonID.PLAY, ButtonID.FOLLOW, ButtonID.TAP_TEMPO, ButtonID.DELETE, ButtonID.LOOP, ButtonID.F3},
		/* 6 */ { null, null, ButtonID.DEVICE, ButtonID.MIXER, ButtonID.DEVICE_ON_OFF, ButtonID.PAGE_RIGHT, ButtonID.ADD_EFFECT, ButtonID.NEW},
		/* 7 */ { null, null, null, ButtonID.AUTOMATION, ButtonID.ADD_TRACK, ButtonID.PAGE_LEFT, ButtonID.LAYOUT_ARRANGE, ButtonID.DRUM},
		/* 8 */ { ButtonID.MASTERTRACK_TOUCH, ButtonID.ROW1_7, ButtonID.ROW1_6, ButtonID.ROW1_5, ButtonID.ROW1_4, ButtonID.ROW1_3, ButtonID.ROW1_2, ButtonID.ROW1_1 },
	};


	/** Mapping that stores which byte is controlled by a given illumination spot in our update button report. */
	private final ButtonID[] BUTTON_ILLUMINATION_MAPPINGS = {
		ButtonID.DRUM,           // Channel
		ButtonID.DEVICE,         // Plug-in
		ButtonID.LAYOUT_ARRANGE, // Arranger
		ButtonID.MIXER,
		ButtonID.BROWSE,
		ButtonID.DEVICE_ON_OFF,  // Sampling
		ButtonID.PAGE_LEFT,
		ButtonID.PAGE_RIGHT,
		ButtonID.ADD_TRACK,      // File
		ButtonID.ADD_EFFECT,     // Settings
		ButtonID.AUTOMATION,     // Auto
		ButtonID.NEW,            // Macro
		ButtonID.ROW1_1,
		ButtonID.ROW1_2,
		ButtonID.ROW1_3,
		ButtonID.ROW1_4,
		ButtonID.ROW1_5,
		ButtonID.ROW1_6,
		ButtonID.ROW1_7,
		ButtonID.ROW1_8,
		ButtonID.VOLUME,
		ButtonID.SWING,
		ButtonID.REPEAT,         // Note repeat
		ButtonID.TEMPO_TOUCH,    // Tempo
		ButtonID.OVERDUB,        // Lock
		ButtonID.F1,             // Pitch
		ButtonID.F2,             // Mod
		ButtonID.F3,             // Perform
		ButtonID.F4,             // Notes
		ButtonID.TRACK_SELECT_1, // 'A'
		ButtonID.TRACK_SELECT_2, // 'B'
		ButtonID.TRACK_SELECT_3, // 'C'
		ButtonID.TRACK_SELECT_4, // 'D'
		ButtonID.TRACK_SELECT_5, // 'E'
		ButtonID.TRACK_SELECT_6, // 'F'
		ButtonID.TRACK_SELECT_7, // 'G'
		ButtonID.TRACK_SELECT_8, // 'H'
		ButtonID.LOOP,           // Restart
		ButtonID.DELETE,         // Erase
		ButtonID.TAP_TEMPO,      // Tap/Metro
		ButtonID.FLIP,           // Follow
		ButtonID.PLAY,
		ButtonID.RECORD,
		ButtonID.STOP,
		ButtonID.SHIFT,
		ButtonID.ACCENT,         // Fixed Velocity
		ButtonID.ROW2_1,         // Pad Mode
		ButtonID.ROW2_2,         // Keyboard
		ButtonID.ROW2_3,         // Chords
		ButtonID.ROW2_4,         // Step
	    ButtonID.SCENE1,         // Scene
		ButtonID.CLIP,           // Pattern
		ButtonID.NOTE,           // Events
		ButtonID.TOGGLE_DEVICE,  // Variation
		ButtonID.DUPLICATE,
		ButtonID.SELECT,
		ButtonID.SOLO,
		ButtonID.MUTE,
		ButtonID.UP,
		ButtonID.LEFT,
		ButtonID.RIGHT,
		ButtonID.DOWN
	};

	
	//
	// Controller state.
	//
	private static final int PAD_COUNT = 16;
	private static final int NOTE_COUNT = 128;
	private static final int TOUCHSTRIP_LED_COUNT = 25;
	
	private static final int TOUCHSTRIP_LED_OFFSET = 0;
	private static final int PAD_COLOR_OFFSET      = 25;

	public static final int KNOB_COUNT = 8;
	public static final int BUTTON_BYTES = 9;
	
	/** Stores the current colors for each pad; so we can update one without updating all. */
	private byte [] noteColor = new byte [NOTE_COUNT];
	private byte [] touchstripLedColor = new byte [TOUCHSTRIP_LED_COUNT];

	/** Stores the current pressures for each pad, so we know when to issue a note-on, AT, or note off. */
	private int [] currentPadPressure = new int [PAD_COUNT];
	
	/** Stores whether a given encoder is being touched. */
	protected boolean [] knobIsTouched = new boolean[KNOB_COUNT];

	/** Stores the current value of a given knob. */
	protected double [] knobValue      = new double[KNOB_COUNT];

	/** Stores the raw state of our previous button bytes, for event generation. */
	private byte [] previousButtonStates = new byte [BUTTON_BYTES];

	//
	// Callbacks.
	//
	//
	
	private IButtonEventCallback knobTouchChangeCallback;
	private IContinuousChangeCallback knobValueChangeCallback;




    /**
     * Connect to the USB port and claim the display interface.
     *
     * @param host The controller host
     */
    public MaschineUsb (final IHost host, final MaschineConfiguration configuration, final IModel model, final IMidiInput input) throws UsbException
    {
		this.configuration = configuration;
		this.model = model;
		this.input = input;

		// Connect to our USB device...
		this.usbDevice = host.getUsbDevice (0);

		// ... get the core HID device we'll use to interface with it...
		this.hidDevice = this.usbDevice.getHidDevice().get();

		// ... and get the endpoint we'll use to splat pixels up at the device.
		try {
			this.usbDisplayEndpoint = this.usbDevice.getEndpoint (0, 0);
		}
		catch (UsbException ex) {
			host.error("Could not open endpoint for display: " + ex.toString());
			this.usbDisplayEndpoint = null;
		}

		// Create data buffers for our HID reports...
		this.reportOutputBlock = host.createMemoryBlock (MAX_OUTPUT_REPORT_SZ);

		// ... set up image buffers..
		this.displayBlockLeft = host.createMemoryBlock (DISPLAY_PACKET_SIZE);
		this.displayBlockRight = host.createMemoryBlock (DISPLAY_PACKET_SIZE);

		// ... and copy the headers into our packets...
		for (int i = 0; i < DISPLAY_HEADER_LEFT.length; ++i) {
			byteStoreLeft[i] = DISPLAY_HEADER_LEFT[i];
			byteStoreRight[i] = DISPLAY_HEADER_RIGHT[i];
		}

		// ... and the footer.
		final int footerOffset = DISPLAY_PACKET_SIZE - DISPLAY_FOOTER.length;
		for (int i = 0; i < DISPLAY_FOOTER.length; ++i) {
			byteStoreLeft[i + footerOffset] = DISPLAY_FOOTER[i];
			byteStoreRight[i + footerOffset] = DISPLAY_FOOTER[i];
		}
    }

	/** Sets the associated surface. Must be called before this can be used. */
	public void setSurface(MaschineControlSurface surface) {
		this.surface = surface;
		this.hidDevice.setCallback ( (reportID, data, received) -> this.processHIDMessage (reportID, data));
	}

	
	/**
	 * Returns true iff we were able to open the device's display.
	 */
	public boolean hasDisplay() {
		return (this.usbDisplayEndpoint != null);
	}

	
	/**
	 * Process any HID messages received from the device.
	 *
	 * @param reportId A number specifying what type of data we're receving.
	 * @param dara The raw data received.
	 */
	private void processHIDMessage (final byte reportID, final byte [] data) {
		switch(reportID) {
			case REPORT_DIGITAL_UPDATE:
				this.processHIDDigitalUpdate(data);
				break;
			case REPORT_ANALOG_UPDATE:
				this.processHIDAnalogUpdate(data);
				break;
			default:
				this.surface.getHost().error("Unknown HID report received!" + Byte.toString(reportID));
				break;
		}
	}


	/** Registers a callback to occur whenever a knob is touched. */
	public void registerKnobTouchCallback(IButtonEventCallback callback) {
		this.knobTouchChangeCallback = callback;
	}

	/** Registers a callback to occur whenever a knob is turned. */
	public void registerKnobValueChangeCallback(IContinuousChangeCallback callback) {
		this.knobValueChangeCallback = callback;
	}


	/** Indicates whether a given knob is touched. **/
	public boolean isKnobTouched(final int knobNumber) {
		return this.knobIsTouched[knobNumber];
	}

	/** Sets whether a given knob is being touched. **/
	protected void setKnobTouched(final int knobNumber, boolean touched) {
		final boolean previousState = this.knobIsTouched[knobNumber];
		this.knobIsTouched[knobNumber] = touched;

		// Convert our button up and down events into CC.
		//
		// Amusingly, these will be converted back and sent to us by Bitwig; but having them be
		// represented as CCs in the middle enables them to be fed to other places.
		if (!previousState && touched) {
			if (this.knobTouchChangeCallback != null) {
				this.knobTouchChangeCallback.callback(knobNumber, ButtonEvent.DOWN);
			}
		} else if (previousState && !touched) {
			if (this.knobTouchChangeCallback != null) {
				this.knobTouchChangeCallback.callback(knobNumber, ButtonEvent.UP);
			}
		}
	}

	/** Sets the value associated with a given knob control. */
	protected void setKnobValue(final int knobNumber, double value) {
		final double previousValue = this.knobValue[knobNumber];
		this.knobValue[knobNumber] = value;

		// If the value has changed, report the new value.
		if ((previousValue != value) && (this.knobValueChangeCallback != null)) {
			this.knobValueChangeCallback.callback(knobNumber, value, previousValue);
		}
	}
	
	
	/** The maximum value we can see on our 'mode knob' encoders. */
	private final int KNOB_MAX_VALUE = 0x03ff;


	/** Scales a 'mode knob' encoder value to a MIDI double. */
	private double normalizeKnobValue(final int knobValue) {
		return (double)knobValue / KNOB_MAX_VALUE;	
	}


	/** Processes an update to the state of the Maschine's buttons and knobs. */
	private void processHIDDigitalUpdate (final byte [] data) {

		final int touchStateByte = 9;

		final int knobBytesOffset = 11;
		final int knobValueSize   = 2;

		//
		// Button states.
		//
		
		// Iterate through every _bit_ of our button bytes.
		for (int byteIndex = 0; byteIndex < BUTTON_BYTES; ++byteIndex) {
			for (int buttonIndex = 0; buttonIndex < 8; ++buttonIndex) {
				final ButtonID buttonIdentifier = BUTTON_INPUT_MAPPINGS[byteIndex][7 - buttonIndex];

				// If we don't have a button mapping for this bit, it's unused. Skip it.
				if (buttonIdentifier == null) {
					continue;
				}

				// If we don't have a button _handler_ for this button, skip it.
				final IHwButton button = this.surface.getButton(buttonIdentifier);
				if (button == null) {
					continue;
				}

				// Otherwise, check the button's state.
				final byte buttonMask          = (byte)(1 << buttonIndex);
				final boolean currentlyPressed = (data[byteIndex] & buttonMask) != 0;
				final boolean wasPressed       = (this.previousButtonStates[byteIndex] & buttonMask) != 0;

				// Handle key-downs and key-ups.
				if (currentlyPressed && !wasPressed) {
					button.trigger(ButtonEvent.DOWN);	
				}
				if (!currentlyPressed && wasPressed) {
					button.trigger(ButtonEvent.UP);	
				}
			}

			this.previousButtonStates[byteIndex] = data[byteIndex];
		}


		//
		// Knob states.
		//
		
		// First, detect whether each knob is being touched...
		final byte encoderTouchState = data[9];
		for (int i = 0; i < KNOB_COUNT; ++i) {

			// The general abstraction counts the leftmost knob as Knob 0;
			// but our value comes with the rightmost knob as Knob 0.
			// Flip the value around.
			final int knobNumber = (KNOB_COUNT - 1) - i;

			final byte mask = (byte)(1 << i);
			this.setKnobTouched (knobNumber, (encoderTouchState & mask) != 0);
		}

		// ... and then extract its current encoder value.
		for (int i = 0; i < KNOB_COUNT; ++i) {
			final int knobOffset = knobBytesOffset + (knobValueSize * i);

			// Parse out the encoder's raw value...
			final int knobLsb   = data[knobOffset + 0];
			final int knobMsb   = data[knobOffset + 1];
			final int knobValue = (knobMsb << 8) | knobLsb; 

			// ... and use it.
			this.setKnobValue(i, this.normalizeKnobValue(knobValue));
		}


	}

	
	private final int PAD_PRESSURE_MIN = 0x4000;
	private final int PAD_PRESSURE_MAX = 0x4fff;

	/** Converts a pressure into a MIDI velocity. */
	private int pressureToVelocity (final int pressure) {
		final double maxPressure = PAD_PRESSURE_MAX - PAD_PRESSURE_MIN;

        final double maxMidiValue = 127;
        final double medianMidiValue = maxMidiValue / 2;

		final double positionOnCurve = pressure / maxPressure;
		final double linearMidiValue = positionOnCurve * 127;

		// FIXME: get this curve factor from our configuration, not manually
		// 0 gives a linear curve; positive values create a convex polynomial with its extrema
		// pointing up; negative with an extema leveling out to the side.
		final double curveFactor = -0.85;
        final double qValue = medianMidiValue + (curveFactor * medianMidiValue);

		// Compute our curve.
		final double quadComponentA = (2 * (1 - positionOnCurve) * positionOnCurve * qValue);
		final double quadComponentB = (positionOnCurve * positionOnCurve * maxMidiValue);
        final double secondOrderComponent = Math.round(quadComponentA + quadComponentB);

		return (int)((linearMidiValue - secondOrderComponent) + linearMidiValue);
    }

	/** Processes a pad update and converts it to MIDI events */
	private void processHIDAnalogUpdate (final byte [] data) {

		int [] newPadPressures = new int[PAD_COUNT];
		Arrays.fill(newPadPressures, -1);

		//
		// First, extract each of our pad pressures from the packet.
		//
		for (int i = 0; i < PAD_COUNT; ++i ) {
			final int dataTriple = i * 3;
			
			// Split the data into pad and pressure.
			int pad      = this.byteNumberForPad(data[dataTriple]);
			int pressure = (data[dataTriple + 1] << 8) | (data[dataTriple + 2]);

			// If we have more than our minimum pressure, we have a new reading.
			if (pressure >= PAD_PRESSURE_MIN) {
				newPadPressures[pad] = pressure - PAD_PRESSURE_MIN;
			}
		}

		//
		// Next: Convert the pad pressures to midi events.
		//
		for (int i = 0; i < PAD_COUNT; ++i ) {
			final int newPressure = newPadPressures[i];
			final int oldPressure = this.currentPadPressure[i];

			final int note = this.getNoteForPad(i);

			// FIXME: determine this ascendingly; and not just by the pad
			final int mpeChannel = i;

			if (newPressure == -1) {
				continue;
			}
			
			// Case 1: the button wasn't pressed, but it's become pressed.
			// Issue a NOTE ON with the initial pressure as the velocity.
			//
			// FIXME: this should be pressure /over time/; do that in a sec
			if ((oldPressure == 0) && (newPressure != 0)) {
				this.input.sendRawMidiEvent(MidiConstants.CMD_NOTE_ON | mpeChannel, note, this.pressureToVelocity(newPadPressures[i]));
				continue;
			}

			// Case 2: the button is pressed, but its value has changed.
			// Issue an aftertouch event.
			if ((oldPressure != 0) && (newPressure != 0) && (newPressure != oldPressure)) {
				this.input.sendRawMidiEvent(MidiConstants.CMD_POLY_AFTERTOUCH | mpeChannel, note, this.pressureToVelocity(newPadPressures[i]));
				continue;
			}

			// Case 3: the button was pressed, but is now released.
			// Issue a NOTE_OFF event.
	 		if ((oldPressure != 0) && (newPressure == 0)) {
				this.input.sendRawMidiEvent(MidiConstants.CMD_NOTE_OFF | mpeChannel, note, 0);
				continue;
			}

		}

		// Adopt the current pad pressures.
		for (int i = 0; i < PAD_COUNT; ++i) {
			if (newPadPressures[i] != -1) {
				this.currentPadPressure[i] = newPadPressures[i];
			}
		}
	}


    /**
     * Send the buffered image to the screen.
     *
     * @param image An image of size 960 x 272 pixels.
     */
    public void sendDisplay (final IBitmap image)
    {
		if (this.usbDisplayEndpoint == null) {
			return;
		}

        // Copy to the buffer
        synchronized (this.screenBufferUpdateLock)
        {
            image.encode ( (imageBuffer, width, height) -> {

				// Start filling our buffers right after the display header.
                int leftIndex = DISPLAY_HEADER_LEFT.length;
                int rightIndex = DISPLAY_HEADER_RIGHT.length;

				final int screenSplitBoundary = width / 2;

                for (int y = 0; y < height; y++)
                {
                    for (int x = 0; x < width; x++)
                    {
                        final int blue = imageBuffer.get ();
                        final int green = imageBuffer.get ();
                        final int red = imageBuffer.get ();
                        imageBuffer.get (); // Drop unused Alpha

                        final int pixel = sPixelFromRGB (red, green, blue);
						
						// If this is on the left half of our final image, stick the pixels
						// in the left byte store...
						if (x < screenSplitBoundary) {
							this.byteStoreLeft[leftIndex + 1] = (byte) (pixel & 0x00FF);
							this.byteStoreLeft[leftIndex] = (byte) ((pixel & 0xFF00) >> 8);
							leftIndex += 2;
						} 
						// Otherwise, we'll render into the buffer for the right screen.
						else {
							this.byteStoreRight[rightIndex + 1] = (byte) (pixel & 0x00FF);
							this.byteStoreRight[rightIndex] = (byte) ((pixel & 0xFF00) >> 8);
							rightIndex += 2;
						}
                    }
                }

                imageBuffer.rewind ();
            });

        }

        synchronized (this.sendLock)
        {
            if (!this.sendExecutor.isShutdown ())
                this.sendExecutor.submit (this::sendDisplayData);
        }
    }


    private void sendDisplayData ()
    {
		if (this.usbDisplayEndpoint == null) {
			return;
		}

        // Copy the data from the buffer to the USB block
        synchronized (this.screenBufferUpdateLock)
        {
            final ByteBuffer leftBuffer = this.displayBlockLeft.createByteBuffer ();
            final ByteBuffer rightBuffer = this.displayBlockRight.createByteBuffer ();
            
			leftBuffer.clear ();
			rightBuffer.clear ();
			leftBuffer.put (this.byteStoreLeft);
			rightBuffer.put (this.byteStoreRight);
        }

        // Send the data
        synchronized (this.sendLock)
        {
            if (this.usbDevice == null || this.usbDisplayEndpoint == null) {
                return;
			} 

			// Update our displays.
            this.usbDisplayEndpoint.send (this.displayBlockLeft, TIMEOUT);
            this.usbDisplayEndpoint.send (this.displayBlockRight, TIMEOUT);
        }
    }


    private static int sPixelFromRGB (final int red, final int green, final int blue)
    {
        int pixel = (blue & 0xF8) >> 3;
        pixel <<= 6;
        pixel += (green & 0xFC) >> 2;
        pixel <<= 5;
        pixel += (red & 0xF8) >> 3;
        return pixel;
    }

	//
	// General.
	//

	private void killAllLeds() {
		Arrays.fill(this.byteStoreReport, (byte)0);

		// Create a zeroed buffer for clearing out our state...
		final ByteBuffer buffer = this.reportOutputBlock.createByteBuffer ();
		buffer.put(this.byteStoreReport);

		// ... and turn off all our button LEDs.
		this.hidDevice.sendOutputReport(REPORT_UPDATE_BUTTONS, this.reportOutputBlock);
	}


    /**
     * Stops all transfers to the device. Nulls the device.
     */
    public void shutdown ()
    {
        synchronized (this.sendLock)
        {
			this.killAllLeds();

            this.usbDevice = null;
            this.usbDisplayEndpoint = null;

            this.sendExecutor.shutdown ();
            try
            {
                if (!this.sendExecutor.awaitTermination (5, TimeUnit.SECONDS))
                    this.surface.getHost().error ("USB Send executor did not end in 5 seconds.");
            }
            catch (final InterruptedException ex)
            {
                this.surface.getHost().error ("USB Send executor interrupted.", ex);
                Thread.currentThread ().interrupt ();
            }
        }
    }


    /**
     * Check if the send executor is shutdown.
     *
     * @return True if shutdown
     */
    public boolean isShutdown ()
    {
        return this.sendExecutor.isShutdown ();
    }


	/** {@inheritDocs} */
	public void setNoteColor(final int note, final int padColor) {
		this.noteColor[note] = (byte)padColor;
		this.updatePadsAndTouchstrip();
	}


	/** 
	 * Updates the controller's state, setting button and LED colors. 
	 * Does not update the screen.
	 */
	public void update() 
	{
		this.updatePadsAndTouchstrip();
	}

	/** Returns the note associated with the given pad. */
	private int getNoteForPad(final int padIndex) {
		final Scales scales = this.model.getScales();
		final int [] padOffsetMatrix = scales.getActiveMatrix();

		return padOffsetMatrix[padIndex] +  scales.getStartNote();
	}


	/** Returns the note associated with the given pad. */
	private int getNoteForPadColor(final int padIndex) {
		final Scales scales = this.model.getScales();
		return padIndex + scales.getStartNote();
	}

	/** Returns the byte index into a Pad report for the given pad. */
	private int byteNumberForPad(final int padIndex) {
		final int row = 3 - (padIndex / 4);
		final int col = padIndex % 4;

		final int position = (row * 4) + col;
		return position;
	}


	/** Updates the colors of the pads and touchstrip LEDs to match their stored state. */
	private void updatePadsAndTouchstrip()
	{
		synchronized (this.hidBufferUpdateLock) {
			Arrays.fill(this.byteStoreReport, (byte)0);
	
			// The report first contains the touchstrip segments...
			for (int i = 0; i < TOUCHSTRIP_LED_COUNT; ++i) {
				this.byteStoreReport[i + TOUCHSTRIP_LED_OFFSET] = this.touchstripLedColor[i];
			}

			// ... and then the pad colors.
			for (int i = 0; i < PAD_COUNT; ++i) {
				this.byteStoreReport[this.byteNumberForPad(i) + PAD_COLOR_OFFSET] = this.noteColor[this.getNoteForPadColor(i)];
			}

			// Transfer our data into a memory block...
            final ByteBuffer buffer = this.reportOutputBlock.createByteBuffer ();
			buffer.put(this.byteStoreReport);

			// ... and send it.
			this.hidDevice.sendOutputReport(REPORT_UPDATE_PADS, this.reportOutputBlock);
		}
	}


	/** 
	 * Updates the brightness of all buttons according to their states.
	 */
	public void updateButtonIlluminations()
	{
		synchronized (this.hidBufferUpdateLock) {
			Arrays.fill(this.byteStoreReport, (byte)0);

			// Get the mode we're working with, which will control our buttons.
			final IMode mode = this.surface.getModeManager().getActive();
			if (mode != null) {

				// Iterate over each illumination in our output report, and fill in a brightness value.
				for (int i = 0; i < BUTTON_ILLUMINATION_MAPPINGS.length; ++i) {
					final ButtonID buttonIdentifier = BUTTON_ILLUMINATION_MAPPINGS[i];

					// Attempt to get the button from either the mode, or view.
					int buttonColor = mode.getButtonColor(buttonIdentifier);
					this.byteStoreReport[i] = (byte)buttonColor;
				}
			}

			// Transfer our data into a memory block...
            final ByteBuffer buffer = this.reportOutputBlock.createByteBuffer ();
			buffer.put(this.byteStoreReport);

			// ... and send it.
			this.hidDevice.sendOutputReport(REPORT_UPDATE_BUTTONS, this.reportOutputBlock);
		}
	}


}
