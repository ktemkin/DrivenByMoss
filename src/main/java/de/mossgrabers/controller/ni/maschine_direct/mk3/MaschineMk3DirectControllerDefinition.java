// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3;

import de.mossgrabers.framework.controller.DefaultControllerDefinition;
import de.mossgrabers.framework.usb.UsbMatcher;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.utils.Pair;

import java.util.Collections;
import java.util.List;
import java.util.UUID;



/**
 * Definition class for the NI Maschine Mk3 controller extension.
 *
 * @author Jürgen Moßgraber and Kate Temkin
 */
public class MaschineMk3DirectControllerDefinition extends DefaultControllerDefinition
{
    private static final UUID EXTENSION_ID = UUID.fromString ("EB426C5C-D2D2-4599-A7F6-8DE9E0A4D55A");

    /** Maschine Mk3 USB Vendor ID. */
    private static final short VENDOR_ID          = 0x17CC;
    /** Maschine Mk3 USB Product ID. */
    private static final short PRODUCT_ID         = 0x1600;
	/** The display interface on a Maschine Mk3. */
	private static final byte DISPLAY_INTERFACE   = 5;
	/** The display endpoint on a Maschine Mk3. */
	private static final byte DISPLAY_ENDPOINT    = 4;

    /**
     * Constructor.
     */
    public MaschineMk3DirectControllerDefinition ()
    {
        super (EXTENSION_ID, "Maschine Mk3 (Direct)", "Native Instruments", 1, 0);
    }

    /** {@inheritDoc} */
    @Override
    public UsbMatcher claimUSBDevice ()
    {
        return new UsbMatcher (VENDOR_ID, PRODUCT_ID, DISPLAY_INTERFACE, DISPLAY_ENDPOINT, true);
    }
}
