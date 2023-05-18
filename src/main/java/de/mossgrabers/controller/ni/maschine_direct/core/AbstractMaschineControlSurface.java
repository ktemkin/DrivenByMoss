package de.mossgrabers.controller.ni.maschine_direct.core;

import de.mossgrabers.controller.ni.maschine_direct.MaschineDirect;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.AbstractControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Abstract implementation of the Maschine Control Surface.
 *
 * @param <C> The type of the configuration
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractMaschineControlSurface<C extends Configuration> extends AbstractControlSurface<C>
{
    protected final MaschineDirect maschine;


    /**
     * Constructor.
     *
     * @param host The host
     * @param configuration The configuration
     * @param maschine The maschine description
     * @param colorManager
     * @param output The MIDI output
     * @param input The MIDI input
     * @param padGrid The pads if any, may be null
     * @param width The physical width of the controller device in mm
     * @param height The physical height of the controller device in mm
     */
    protected AbstractMaschineControlSurface (final IHost host, final C configuration, final ColorManager colorManager, final MaschineDirect maschine, final IMidiOutput output, final IMidiInput input, final IPadGrid padGrid, final double width, final double height)
    {
        super (host, configuration, colorManager, output, input, padGrid, width, height);

        this.maschine = maschine;

		if (this.input != null) {
			this.input.setSysexCallback (this::handleSysEx);
		}
    }


    /**
     * Get the Maschine object.
     *
     * @return The Maschine object
     */
    public MaschineDirect getMaschine ()
    {
        return this.maschine;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isShiftPressed ()
    {
		return this.isPressed(ButtonID.SHIFT);
    }


    /**
     * Handle incoming system exclusive data.
     *
     * @param data The data
     */
    protected void handleSysEx (final String data)
    {
		// We don't actually talk MIDI directly, so there's nothing to handle.
    }
}
