// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.mode;

import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.core.MaschineColorManager;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.daw.data.bank.IBank;
import de.mossgrabers.framework.featuregroup.AbstractParameterMode;
import de.mossgrabers.framework.controller.ButtonID;

import java.util.List;


/**
 * Base class for all Maschine modes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class BaseMode<B extends IItem> extends AbstractParameterMode<MaschineControlSurface, MaschineConfiguration, B>
{
    protected int selectedParam = 0;


    /**
     * Constructor.
     *
     * @param name The name of the mode
     * @param surface The control surface
     * @param model The model
     */
    protected BaseMode (final String name, final MaschineControlSurface surface, final IModel model)
    {
        super (name, surface, model, false);
    }


    /**
     * Constructor.
     *
     * @param name The name of the mode
     * @param surface The control surface
     * @param model The model
     * @param controls The IDs of the knobs or faders to control this mode
     */
    protected BaseMode (final String name, final MaschineControlSurface surface, final IModel model, final IBank<B> bank)
    {
        super (name, surface, model, true, bank, DEFAULT_KNOB_IDS);
    }


    /**
     * Add a marker (>) if the index equals the selected parameter.
     *
     * @param label The label to eventually add the marker
     * @param index The index
     * @return The formatted text
     */
    protected String mark (final String label, final int index)
    {
        if (this.selectedParam == index)
            return ">" + label;
        return label;
    }

	
    /** {@inheritDoc} */
    @Override
    public void selectPreviousItem ()
    {
        this.selectedParam = Math.max (0, this.selectedParam - 1);
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItem ()
    {
        this.selectedParam = Math.min (7, this.selectedParam + 1);
    }


    /** {@inheritDoc} */
    @Override
    public void selectItem (final int index)
    {
        this.selectedParam = index;
    }


    /**
     * Get the selected item (edit index).
     *
     * @return The edit index
     */
    public int getSelectedItem ()
    {
        return this.selectedParam;
    }


	/** {@inheritDocs} */
	@Override
	public int getButtonColor(final ButtonID button) {
		final int hi = MaschineColorManager.BRIGHTNESS_HI;
		final int on = MaschineColorManager.BRIGHTNESS_ON;


		switch (button) {

			// Shift should be highlighted whenever it's pressed.
			case SHIFT:
				return this.surface.isShiftPressed() ? hi : on;

			// Play/stop should always be available; and bright when playing.
			case PLAY:
			case STOP:
				return this.model.getTransport().isPlaying() ? hi : on;

			// Record should be bright when armed.
			case RECORD:
				return this.model.getTransport().isRecording() ? hi : on;



			// Mode buttons. These should be overridden in their respective modes
			// to be 'hi'.
			case ROW1_1:
			case ROW1_2:
			case ROW1_3:
			case ROW1_4:
			case VOLUME:
			case ROW2_2:
				return on;


			default:
				return super.getButtonColor(button);
		}
	}

}
