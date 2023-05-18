// Written by Jürgen Moßgraber - mossgrabers.de
// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.core.controller;


/** Interface representing a direct connection to a Maschine device. */
public interface IMaschineDirectConnection {


	/** Applies a color to a given pad. */
	void setNoteColor(final int note, final int padColor);


}

