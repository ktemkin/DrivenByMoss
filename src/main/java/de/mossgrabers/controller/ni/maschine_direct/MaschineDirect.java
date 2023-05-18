// Written by Kate Temkin - ktemk.in
// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct;

/**
 * The supported Maschine models.
 *
 * @author Kate Temkin
 * @author Jürgen Moßgraber 
 */
public enum MaschineDirect
{
    /** Maschine Mk3. */
    MK3("Maschine Mk3 (Direct Access)", "1600D");

    private final String        name;
    private final String        maschineID;

    /**
     * Constructor.
     *
     * @param name The name of the Maschine
     * @param maschineID The ID of the device
     * @param height The height of the simulator window
     * @param footswitches The number of available footswitch on the Maschine
     */
    private MaschineDirect (final String name, final String maschineID)
    {
        this.name = name;
        this.maschineID = maschineID;
    }


    /**
     * Get the name of the specific Maschine.
     *
     * @return The name
     */
    public String getName ()
    {
        return this.name;
    }



    /**
     * Get the width of the simulator window
     *
     * @return The height
     */
    public double getWidth ()
    {
        return 420;
    }


    /**
     * Get the height of the simulator window
     *
     * @return The height
     */
    public int getHeight ()
    {
        return 272;
    }


    /**
     * Get the number of foot switches on the Maschine.
     *
     * @return The number
     */
    public int getFootswitches ()
    {
        return 2;
    }
}
