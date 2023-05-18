// Written by Kate Temkin - ktemk.in
// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.mk3.controller;

import de.mossgrabers.controller.ni.maschine_direct.mk3.MaschineConfiguration;
import de.mossgrabers.framework.controller.display.AbstractGraphicDisplay;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.graphics.DefaultGraphicsDimensions;
import de.mossgrabers.framework.graphics.IBitmap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * The display of Push 2.
 *
 * @author Jürgen Moßgraber and Kate Temkin
 */
public class MaschineDisplay extends AbstractGraphicDisplay
{
    private boolean                isShutdown = false;
	private MaschineControlSurface surface;


    /**
     * Constructor. A virtual LCD display of 960x272 pixels spread across two actual 480-pixel-wide displays.
     *
     * @param host The host
     * @param maxParameterValue The maximum parameter value (upper bound)
     * @param configuration The Maschine configuration
     */
    public MaschineDisplay (final IHost host, final int maxParameterValue, final MaschineConfiguration configuration, final MaschineControlSurface surface)
    {
        super (host, configuration, new DefaultGraphicsDimensions (480 * 2, 272, maxParameterValue), "Maschine Display", true);
		this.surface = surface;
    }


    /** {@inheritDoc} */
    @Override
    public void notify (final String message)
    {
        if (message == null)
            return;
        this.host.showNotification (message);
        this.setNotificationMessage (message);
    }




    /** {@inheritDoc} */
    @Override
    public void shutdown ()
    {
        this.send ();

        this.isShutdown = true;

        final ExecutorService executor = Executors.newSingleThreadExecutor ();
        executor.execute ( () -> {
            super.shutdown ();
        });
        executor.shutdown ();
        try
        {
            executor.awaitTermination (10, TimeUnit.SECONDS);
        }
        catch (final InterruptedException ex)
        {
            this.host.error ("Display shutdown interrupted.", ex);
            Thread.currentThread ().interrupt ();
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void send (final IBitmap image)
    {
		this.surface.getUsb().sendDisplay(image);
    }
}
