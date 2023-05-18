// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.command.trigger;

import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Command to handle the shift button.
 *
 * @author Jürgen Moßgraber
 */
public class ShiftCommand extends AbstractTriggerCommand<MaschineControlSurface, MaschineConfiguration>
{
    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public ShiftCommand (final IModel model, final MaschineControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ModeManager modeManager = this.surface.getModeManager ();
        final Modes cm = modeManager.getActiveID ();

		/*
        if (event == ButtonEvent.DOWN && Modes.SCALES.equals (cm))
            modeManager.setTemporary (Modes.SCALE_LAYOUT);
        else if (event == ButtonEvent.UP && Modes.SCALE_LAYOUT.equals (cm))
            modeManager.restore ();
		*/

        this.surface.setKnobSensitivityIsSlow (this.surface.isShiftPressed ());
    }
}
