// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.mode;

import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.display.ITextDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.scale.Scale;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.Pair;


/**
 * Selection of the scale and base note.
 *
 * @author Jürgen Moßgraber
 */
public class ScalesMode extends BaseMode<IItem>
{
    private final Scales scales;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public ScalesMode (final MaschineControlSurface surface, final IModel model)
    {
        super ("Scale", surface, model);

        this.scales = model.getScales ();
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
		switch (index) {

			// Edit the scale we're actively working with.
			case 0:
			case 1:
				this.scales.changeScale (value);
				this.update ();
				break;
			case 2:
			case 3:
				// FIXME: change the offset
				break;

		}

    }


	/** {@inheritDocs} */
	@Override
	public int getButtonColor(final ButtonID button) {
		switch (button) {
			case ROW2_2:
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

		// List the various types of scales we support, for selection.
        final int scaleTypeIndex = this.scales.getScale ().ordinal ();
        display.addListElement (6, Scale.getNames (), scaleTypeIndex);

		// List the various keys, for selection.
		final int scaleOffsetIndex = this.scales.getScaleOffsetIndex();
        display.addListElement (6, Scales.BASES.toArray(new String[0]), scaleOffsetIndex);

		// Provide an option to switch between Chromatic or In-Key.
        display.addOptionElement ("", this.scales.isChromatic () ? "Chromatc" : "In Key", this.scales.isChromatic (), "", "", false, false);
		display.send ();
    }


    private void update ()
    {
        this.surface.getViewManager ().getActive ().updateNoteMapping ();
        final MaschineConfiguration config = this.surface.getConfiguration ();
        config.setScale (this.scales.getScale ().getName ());
        config.setScaleBase (Scales.BASES.get (this.scales.getScaleOffsetIndex ()));
        config.setScaleInKey (!this.scales.isChromatic ());
    }
}
