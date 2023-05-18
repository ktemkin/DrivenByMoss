// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;

import de.mossgrabers.framework.controller.display.AbstractGraphicDisplay;
import de.mossgrabers.framework.controller.display.Format;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameterprovider.track.PanParameterProvider;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;

/**
 * Mode for editing the panorama of all tracks.
 *
 * @author Jürgen Moßgraber
 */
public class PanMode extends AbstractTrackMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public PanMode (final MaschineControlSurface surface, final IModel model)
    {
        super ("Panorama", surface, model);

        this.setParameterProvider (new PanParameterProvider (model));
    }


	/** {@inheritDocs} */
	@Override
	public int getButtonColor(final ButtonID button) {
		switch (button) {
			// Make this mode brighter than the others.
			case ROW1_2:
				return MaschineColorManager.BRIGHTNESS_HI;
			default:
				return super.getButtonColor(button);
		}
	}


    /** {@inheritDoc} */
    @Override
    public void updateDisplay ()
    {
		final IGraphicDisplay display = this.surface.getGraphicsDisplay();
        this.updateChannelDisplay (display, AbstractGraphicDisplay.GRID_ELEMENT_CHANNEL_PAN, false, true);
		display.send ();
    }
}
