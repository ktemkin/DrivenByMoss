// Written by Kate Temkin - ktemk.in
// Written by JürgenDisplay Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3;

import de.mossgrabers.controller.ni.maschine_direct.MaschineDirect;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineUsb;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineDisplay;
import de.mossgrabers.controller.ni.maschine_direct.mk3.command.continuous.MainKnobRowModeCommand;
import de.mossgrabers.controller.ni.maschine_direct.mk3.command.continuous.TouchstripCommand;
import de.mossgrabers.controller.ni.maschine_direct.mk3.command.trigger.ShiftCommand;
import de.mossgrabers.controller.ni.maschine_direct.mk3.command.trigger.LogCommand;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track.VolumeMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track.PanMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track.CrossfadeMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track.SendMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.device.DeviceBrowserMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.mode.ScalesMode;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.ClipView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.DrumView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.MuteView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.NoteRepeatView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.ParameterView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.PlayView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.SceneView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.SelectView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.ShiftView;
import de.mossgrabers.controller.ni.maschine_direct.mk3.view.SoloView;


import de.mossgrabers.framework.command.trigger.transport.PlayCommand;
import de.mossgrabers.framework.command.trigger.transport.RecordCommand;
import de.mossgrabers.framework.command.trigger.transport.StopCommand;
import de.mossgrabers.framework.command.trigger.mode.ModeSelectCommand;
import de.mossgrabers.framework.command.aftertouch.AftertouchViewCommand;
import de.mossgrabers.framework.command.continuous.KnobRowModeCommand;

import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.AbstractControllerSetup;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.ISetupFactory;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwAbsoluteKnob;
import de.mossgrabers.framework.controller.hardware.IHwRelativeKnob;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.ModelSetup;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.midi.IMidiAccess;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.data.empty.EmptyTrack;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.FrameworkException;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.usb.UsbException;
import de.mossgrabers.framework.controller.color.ColorManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;


/**
 * Support for the NI Maschine controller series.
 *
 * @author Jürgen Moßgraber
 */
public class MaschineControllerSetup extends AbstractControllerSetup<MaschineControlSurface, MaschineConfiguration>
{
    // @formatter:off
    /** The drum grid matrix. */
    private static final int [] DRUM_MATRIX =
    {
         0,  1,  2,  3,  4,  5,  6,  7,
         8,  9, 10, 11, 12, 13, 14, 15,
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1
    };
    // @formatter:on
	

	public static final int KNOB_COUNT = 8;

    private final MaschineDirect          maschine;
    private ShiftView                     shiftView;
	private IMidiInput                    input;

    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param factory The factory
     * @param globalSettings The global settings
     * @param documentSettings The document (project) specific settings
     * @param maschine The specific maschine model
     */
    public MaschineControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings, final MaschineDirect maschine)
    {
        super (factory, host, globalSettings, documentSettings);

        this.maschine = maschine;
        this.colorManager = new MaschineColorManager ();
        this.valueChanger = new TwosComplementValueChanger (128, 1);
        this.configuration = new MaschineConfiguration (host, this.valueChanger, factory.getArpeggiatorModes (), maschine);
    }


    /** {@inheritDoc} */
    @Override
    public void init ()
    {
        super.init ();
    }


    /** {@inheritDoc} */
    @Override
    protected void createScales ()
    {
		// Note: our start and end values are purely synthetic, here -- they exist
		// as defaults, but we get the pad indices from HID instead of as notes.
        this.scales = new Scales (this.valueChanger, 36, 52, 4, 4);
        this.scales.setDrumMatrix (DRUM_MATRIX);
    }


    /** {@inheritDoc} */
    @Override
    protected void createModel ()
    {
        final ModelSetup ms = new ModelSetup ();
        ms.setHasFullFlatTrackList (true);
        ms.setNumTracks (8);
        ms.setNumDevicesInBank (16);
        ms.setNumScenes (16);
        this.model = this.factory.createModel (this.configuration, this.colorManager, this.valueChanger, this.scales, ms);

        final ITrackBank trackBank = this.model.getTrackBank ();
        trackBank.setIndication (true);
        trackBank.addSelectionObserver ( (index, isSelected) -> this.handleTrackChange (isSelected));

        this.activateBrowserObserver (Modes.BROWSER);
    }


    /** {@inheritDoc} */
    @Override
    protected void createSurface ()
    {
		MaschineControlSurface surface = null;
	    MaschineUsb usbInterface = null;

		// Create a MIDI input we'll use to send MIDI events up to the device.
        final IMidiAccess midiAccess = this.factory.createMidiAccess ();
        this.input = midiAccess.createInput ("Maschine Mk3");

		// Create our primary connections to the device...
		try {
			usbInterface = new MaschineUsb(this.host, this.configuration, this.model, this.input);
			surface = new MaschineControlSurface (this.host, this.colorManager, this.maschine, this.configuration, usbInterface, this.input);

			usbInterface.setSurface(surface);
			this.surfaces.add (surface);
		}
		catch (UsbException ex) {
			this.host.error("Failed to create surface / get USB device: " + ex.toString());
		}

		// Note: the two screens on the Maschine are treated like a single screen, so we can ideally eventually
		// share code with e.g. the Akai models and the Push2.
		if ((usbInterface != null) && usbInterface.hasDisplay()) {
			final MaschineDisplay display = new MaschineDisplay (this.host, this.valueChanger.getUpperBound(), this.configuration, surface);
			surface.addGraphicsDisplay (display);
		}
    }


    /** {@inheritDoc} */
    @Override
    protected void createModes ()
    {
        final MaschineControlSurface surface = this.getSurface ();
        final ModeManager modeManager = surface.getModeManager ();

		// Core "top button" modes.
        modeManager.register (Modes.VOLUME, new VolumeMode (surface, this.model));
        modeManager.register (Modes.PAN, new PanMode (surface, this.model));
        modeManager.register (Modes.CROSSFADER, new CrossfadeMode (surface, this.model));

		// FIXME: this should select other sends, too
        modeManager.register (Modes.SEND1, new SendMode (surface, this.model, 1));

		// Scale modes.
		modeManager.register (Modes.SCALES, new ScalesMode(surface, model));

		// Device modes.
        modeManager.register (Modes.BROWSER, new DeviceBrowserMode (surface, this.model));

		// Start off in our "track volume" mode.
        modeManager.setDefaultID (Modes.VOLUME);
    }


    /** {@inheritDoc} */
    @Override
    protected void createViews ()
    {
        final MaschineControlSurface surface = this.getSurface ();
        final ViewManager viewManager = surface.getViewManager ();

        viewManager.register (Views.SCENE_PLAY, new SceneView (surface, this.model));
        viewManager.register (Views.SESSION, new ClipView (surface, this.model));

        final DrumView drumView = new DrumView (surface, this.model);
        viewManager.register (Views.DRUM, drumView);
        viewManager.register (Views.PLAY, new PlayView (surface, this.model, drumView));

        viewManager.register (Views.DEVICE, new ParameterView (surface, this.model));
        viewManager.register (Views.REPEAT_NOTE, new NoteRepeatView (surface, this.model));

        this.shiftView = new ShiftView (surface, this.model);
        viewManager.register (Views.SHIFT, this.shiftView);
    }


    /** {@inheritDoc} */
    @Override
    protected void createObservers ()
    {
        super.createObservers ();

        final MaschineControlSurface surface = this.getSurface ();

        this.configuration.registerDeactivatedItemsHandler (this.model);
        this.createScaleObservers (this.configuration);

        //this.createNoteRepeatObservers (this.configuration, surface);

        //this.activateBrowserObserver (Modes.BROWSER);
    }


    /** {@inheritDoc} */
    @Override
    protected void registerContinuousCommands ()
	{
        final MaschineControlSurface surface = this.getSurface ();
        final ModeManager modeManager = surface.getModeManager ();
        final ViewManager viewManager = surface.getViewManager ();
		final MaschineUsb usb = surface.getUsb();

		//
		// Set up the primary encoder -- the main directional/rotational pad.
		//
			
		// FIXME: do this

		//
		// Set up the secondary encoders -- the little knobs underneath the displays.
		//
		
		// Handle their touch sense..
		usb.registerKnobTouchCallback((index, event) -> {
			final IMode mode = modeManager.getActive();
			if (mode != null) {
				mode.onKnobTouch(index, event == ButtonEvent.DOWN);
			}
		});

		// ... and their encoder value changes, for when they spin.
		IHwRelativeKnob[] modeKnobs = new IHwRelativeKnob[KNOB_COUNT];
		for (int i = 0; i < 8; i++)
		{
			modeKnobs[i] = this.addRelativeKnob (ContinuousID.get (ContinuousID.KNOB1, i), "Knob " + (i + 1), new KnobRowModeCommand<> (i, this.model, surface), MaschineControlSurface.MODE_KNOB_1 + i);
			modeKnobs[i].setIndexInGroup (i);
		}
		usb.registerKnobValueChangeCallback((index, newValue, oldValue) -> {
			double delta = newValue - oldValue;

			// If we just wrapped around, re-normalize our value.
			if ((oldValue > 0.75) && (newValue < 0.25)) {
				delta -= 1;
			}
			if ((oldValue < 0.25) && (newValue > 0.75)) {
				delta += 1;
			}

			// FIXME(ktemkin): handle deltas correctly

			modeKnobs[index].handleValue(delta);
		});

		
		//
		// XXX: remove this once I figure out how to make it not crash without it
		//
        final IHwRelativeKnob knob = this.addRelativeKnob (ContinuousID.MASTER_KNOB, "Encoder", new MainKnobRowModeCommand (this.model, surface), MaschineControlSurface.ENCODER);
        knob.bindTouch ( (event, velocity) -> {
            final IMode mode = modeManager.getActive ();
            if (mode != null && event != ButtonEvent.LONG)
                mode.onKnobTouch (8, event == ButtonEvent.DOWN);
        }, surface.getMidiInput (), BindType.CC, 0, MaschineControlSurface.ENCODER_TOUCH);


        final TouchstripCommand touchstripCommand = new TouchstripCommand (this.model, surface);
        this.addFader (ContinuousID.CROSSFADER, "Touchstrip", touchstripCommand, BindType.CC, MaschineControlSurface.TOUCHSTRIP, false);
        surface.getContinuous (ContinuousID.CROSSFADER).bindTouch (touchstripCommand, surface.getMidiInput (), BindType.CC, 0, MaschineControlSurface.TOUCHSTRIP_TOUCH);

        // Enable aftertouch
        final Views [] views =
        {
            Views.PLAY,
            Views.DRUM,
        };
        for (final Views viewID: views)
        {
            final IView view = viewManager.get (viewID);
            view.registerAftertouchCommand (new AftertouchViewCommand<> (view, this.model, surface));
        }
	}


    /** {@inheritDoc} */
    @Override
    protected void registerTriggerCommands ()
    {
		this.registerTransportCommands();
		this.registerModalCommands();

		// Register SHIFT.
        this.addButton (ButtonID.SHIFT, "Shift", new ShiftCommand(this.model, this.getSurface()), MaschineControlSurface.SHIFT);

    }


	/** Registers commands with per-mode meanings. */
	private void registerModalCommands()
	{
        final MaschineControlSurface surface = this.getSurface ();
        final ModeManager modeManager = surface.getModeManager ();

        this.addButton (ButtonID.ROW1_1, "Volume", new ModeSelectCommand<> (this.model, surface, Modes.VOLUME), MaschineControlSurface.MODE_BUTTON_1);
        this.addButton (ButtonID.ROW1_2, "Pan", new ModeSelectCommand<> (this.model, surface, Modes.PAN), MaschineControlSurface.MODE_BUTTON_2);
        this.addButton (ButtonID.ROW1_3, "Crossfade", new ModeSelectCommand<> (this.model, surface, Modes.CROSSFADER), MaschineControlSurface.MODE_BUTTON_3);

		// FIXME(ktemkin): have this cycle through sends
        this.addButton (ButtonID.ROW1_4, "Sends 1-4", new ModeSelectCommand<> (this.model, surface, Modes.SEND1), MaschineControlSurface.MODE_BUTTON_4);

		// FIXME(ktemkin): have this switch to keyboard mode, too
        this.addButton (ButtonID.ROW2_2, "Keyboard", new ModeSelectCommand<> (this.model, surface, Modes.SCALES), MaschineControlSurface.KEYBOARD);
	}


	/** Registers button handlers for transport-related commands. */
	private void registerTransportCommands()
	{
        final MaschineControlSurface surface = this.getSurface ();

        this.addButton (ButtonID.PLAY, "Play", new PlayCommand<> (this.model, surface), MaschineControlSurface.PLAY);
        this.addButton (ButtonID.RECORD, "Record", new RecordCommand<> (this.model, surface), MaschineControlSurface.REC);
        this.addButton (ButtonID.STOP, "Stop", new StopCommand<> (this.model, surface), MaschineControlSurface.STOP);
	}



    /** {@inheritDoc} */
    @Override
    protected void layoutControls ()
    {
        final MaschineControlSurface surface = this.getSurface ();

        surface.getContinuous (ContinuousID.KNOB1).setBounds (183.5, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB2).setBounds (259.0, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB3).setBounds (334.75, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB4).setBounds (410.25, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB5).setBounds (486.0, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB6).setBounds (561.5, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB7).setBounds (637.0, 226.25, 53.75, 49.25);
        surface.getContinuous (ContinuousID.KNOB8).setBounds (712.75, 226.25, 53.75, 49.25);

		if (surface.getUsb().hasDisplay()) {
			// FIXME: is this right?
			surface.getGraphicsDisplay().getHardwareDisplay ().setBounds (182.75, 111.75, 591.75, 64.5);
		}
    }

    /** {@inheritDoc} */
    @Override
    public void startup ()
    {
        final MaschineControlSurface surface = this.getSurface ();

        surface.getModeManager ().setActive (Modes.VOLUME);
        surface.getViewManager ().setActive (Views.PLAY);
    }


    /** {@inheritDoc} */
    @Override
    public void flush ()
    {
        super.flush ();

        final MaschineControlSurface surface = this.getSurface ();
		surface.getUsb().updateButtonIlluminations();
    }



    /** {@inheritDoc} */
    @Override
    protected BindType getTriggerBindType (final ButtonID buttonID)
    {
        return buttonID == ButtonID.ROW2_7 ? BindType.NOTE : BindType.CC;
    }
}
