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
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.track.CrossfadeParameterProvider;

import java.util.HashMap;
import java.util.Map;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;

/**
 * Mode for editing the cross-fade setting of all tracks.
 *
 * @author Jürgen Moßgraber
 */
public class CrossfadeMode extends AbstractTrackMode
{
    private static final Map<String, String> CROSSFADE_TEXT = new HashMap<> (3);

    static
    {
        CROSSFADE_TEXT.put ("A", "A");
        CROSSFADE_TEXT.put ("B", "       B");
        CROSSFADE_TEXT.put ("AB", "   <> ");
    }


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public CrossfadeMode (final MaschineControlSurface surface, final IModel model)
    {
        super (Modes.NAME_CROSSFADE, surface, model);

        this.setParameterProvider (new CrossfadeParameterProvider (model));
    }


	/** {@inheritDocs} */
	@Override
	public int getButtonColor(final ButtonID button) {
		switch (button) {
			// Make this mode brighter than the others.
			case ROW1_3:
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
        this.updateChannelDisplay (display, AbstractGraphicDisplay.GRID_ELEMENT_CHANNEL_CROSSFADER, false, false);
		display.send ();
    }
}
