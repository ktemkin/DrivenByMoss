// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.core.command;

/** Callback type for button events. */
public interface IContinuousChangeCallback {
	void callback(int index, double newValue, double oldValue);
}
