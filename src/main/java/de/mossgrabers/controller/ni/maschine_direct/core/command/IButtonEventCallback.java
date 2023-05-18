// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.maschine_direct.core.command;

import de.mossgrabers.framework.utils.ButtonEvent;

/** Callback type for button events. */
public interface IButtonEventCallback {
	void callback(int index, ButtonEvent event);
}
