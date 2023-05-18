// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.controller.ni.maschine.mk3;

import de.mossgrabers.bitwig.framework.BitwigSetupFactory;
import de.mossgrabers.bitwig.framework.configuration.SettingsUIImpl;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.bitwig.framework.extension.AbstractControllerExtensionDefinition;
import de.mossgrabers.controller.ni.maschine_direct.MaschineDirect;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineControllerSetup;
import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineMk3DirectControllerDefinition;
import de.mossgrabers.controller.ni.maschine_direct.mk3.controller.MaschineControlSurface;
import de.mossgrabers.framework.controller.IControllerSetup;

import com.bitwig.extension.controller.api.ControllerHost;


/**
 * Definition class for the NI Maschine Mk3 controller, direct access.
 *
 * @author Jürgen Moßgraber and Kate Temkin
 */
public class MaschineMk3DirectExtensionDefinition extends AbstractControllerExtensionDefinition<MaschineControlSurface, MaschineConfiguration>
{
    /**
     * Constructor.
     */
    public MaschineMk3DirectExtensionDefinition ()
    {
        super (new MaschineMk3DirectControllerDefinition ());
    }


    /** {@inheritDoc} */
    @Override
    protected IControllerSetup<MaschineControlSurface, MaschineConfiguration> getControllerSetup (final ControllerHost host)
    {
        return new MaschineControllerSetup (new HostImpl (host), new BitwigSetupFactory (host), new SettingsUIImpl (host, host.getPreferences ()), new SettingsUIImpl (host, host.getDocumentState ()), MaschineDirect.MK3);
    }
}
