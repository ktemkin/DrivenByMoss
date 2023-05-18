// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.core.controller;

import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.grid.BlinkingPadGrid;
import de.mossgrabers.framework.daw.IHost;


/**
 * Implementation of the Maschine grid of pads.
 *
 * @author Jürgen Moßgraber
 */
public class MaschinePadGrid extends BlinkingPadGrid
{
	/** The connectino to our Maschine. */
	private final IMaschineDirectConnection connection;

	/** Our host -- for logging. */
    private final IHost                    host;


    /**
     * Constructor. A 4x4 grid.
     *
     * @param colorManager The color manager for accessing specific colors to use
     * @param output The MIDI output which can address the pad states
     */
    public MaschinePadGrid (final ColorManager colorManager, final IMaschineDirectConnection connection, final IHost host)
    {
        super (colorManager, null, 4, 4, 36);
		this.connection = connection;
		this.host = host;
    }


	/** {@inheritDoc} */
	@Override
    protected void sendPadUpdate(final int note, final int colorIndex)
    {
		this.connection.setNoteColor(note, colorIndex);
	}
}
