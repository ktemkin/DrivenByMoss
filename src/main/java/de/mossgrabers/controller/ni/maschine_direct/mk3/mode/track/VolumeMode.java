// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.mode.track;

import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.framework.controller.display.AbstractGraphicDisplay;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameterprovider.track.VolumeParameterProvider;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;


/**
 * Mode for editing a volume parameter of all tracks.
 *
 * @author Jürgen Moßgraber
 */
public class VolumeMode extends AbstractTrackMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public VolumeMode (final MaschineControlSurface surface, final IModel model)
    {
        super (Modes.NAME_VOLUME, surface, model);

        this.setParameterProvider (new VolumeParameterProvider (model));
    }


	@Override
	public void onKnobValue(final int index, final int value) {

		// Fetch the relevant track...
        final ITrackBank tb = this.model.getCurrentTrackBank ();
		final ITrack t = tb.getItem (index);

		// ... and adjust its volume.
		final int delta = this.model.getValueChanger().decode(value);
		t.setVolume(t.getVolume() + delta);

		super.onKnobValue(index, value);
	}


	/** {@inheritDocs} */
	@Override
	public int getButtonColor(final ButtonID button) {
		switch (button) {
			// Make this mode brighter than the others.
			case ROW1_1:
			case VOLUME:
				return MaschineColorManager.BRIGHTNESS_HI;
			default:
				return super.getButtonColor(button);
		}
	}


    /** {@inheritDoc} */
    @Override
    public void updateDisplay()
    {
		final IGraphicDisplay display = this.surface.getGraphicsDisplay();
        this.updateChannelDisplay (display, AbstractGraphicDisplay.GRID_ELEMENT_CHANNEL_VOLUME, true, false);
		display.send ();
    }
}
