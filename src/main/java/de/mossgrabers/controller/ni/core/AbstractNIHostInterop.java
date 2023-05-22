// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.core;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.OperatingSystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;


/**
 * Code for communicating with the NIHostIntegrationAgent.
 * Allows for functionality like screen control that's not provided for via MIDI/OSC.
 *
 * @author Kate Temkin
 */
public abstract class AbstractNIHostInterop {
	protected IHost host;

	/** The device ID -- matches the device ID used elsewhere; and the USB PID. */
	protected int deviceId;

	/** The device serial, in null-terminated ASCII. Optional. */
	protected ByteBuffer deviceSerialBytes;

	/** Constant used to indicate success in NI's RPC protocol. ASCII for 'true'. */
	protected final static int NI_SUCCESS = 0x74727565;

	/** Constant used to indicate a device was found in power-on position. */
	protected final static int NI_DEVICE_ON = 0x3444e2b;

	/** Constant used to indicate a device was found in power-off position, or no device was found. */
	protected final static int NI_DEVICE_OFF = 0x3444e2d;

	/** The message ID for a "create ports for me" handshake. */
	protected final static int NI_SOFTWARE_ID_MASCHINE2 = 0x4e684d32;

	/** Unknown constant (all ascii) necessary for comms. */
	protected final static int NI_HEADER_CONSTANT = 0x70726d79;

	/** Unknown constant necessary for setting project names. */
	protected final static int NI_PROJECT_NAME_UNKNOWN1 = 0x70001006;

	/** Unknown constant necessary for setting project names. */
	protected final static int NI_PROJECT_NAME_UNKNOWN2 = 0xf6b24000;


	//
	// Messages.
	//


	/** The message ID for a "create ports for me" handshake, in a general / non-device context. */
	protected final static int NI_MSG_HANDSHAKE = 0x03447500;

	/** The message ID for a "create ports for me" handshake, in a per-device contxt. */
	protected final static int NI_MSG_CONNECT   = 0x03444900;

	/** The base length of a handshake message. */
	protected final static int NI_MSG_HANDSHAKE_LENGTH = 5 * 4;

	/** The message ID for a "acknowlege notification port" handshake. */
	protected final static int NI_MSG_ACKNOLWEDGE_NOTIFICATION_PORT = 0x03404300;

	/** The base length of an ACK message. */
	protected final static int NI_MSG_ACKNOLWEDGE_NOTIFICATION_PORT_LENGTH = 4 * 4;

	/** The whole message for requesting our device state .*/
	protected final static byte[] NI_WHOLE_MSG_GET_DEVICE_STATE = {(byte)0x43, (byte)0x71, (byte)0x44, (byte)0x03};

	/** Message that sets the current project name, from the device's perspective. */
	protected final static int NI_MSG_SET_PROJECT_NAME = 0x0349734e;

	/** The base length for a project name message. */
	protected final static int NI_MSG_SET_PROJECT_NAME_LENGTH = (4 * 4) + 1;

	/** The message ID for a "send display data" handshake. 'Dsd' */
	protected final static int NI_MSG_DISPLAY   = 0x03647344;


	//
	// Messages from RE:
	//  0x434300 => Controller Acquire
	//


	//
	// Global background connection to the NIHostIntegrationAgent.
	//
	protected final static HashMap<Integer, AbstractNIHostInterop> globalNIConnections = new HashMap<>();


	/**
	 * Factory method that creates the HostInterop for the relevant platform.
	 *
	 * @param deviceId The ID number (same as the USB PID) of the releveant device type.
	 * @param serial The serial of the device to connect to, or an empty string for a non-device-specific connection.
	 */
	public static AbstractNIHostInterop createInterop(int deviceId, String deviceSerial) throws IOException {
		AbstractNIHostInterop interop = null;

		// If we're looking for a per-device connection, but we don't yet have a global one, open one first.
		if (!deviceSerial.isEmpty() && !AbstractNIHostInterop.globalNIConnections.containsKey(Integer.valueOf(deviceId))) {
			AbstractNIHostInterop.createInterop(deviceId, "");
		}

		// If we're looking for a global connection, and we already have one, short circuit the creation process.
		if (deviceSerial.isEmpty() && AbstractNIHostInterop.globalNIConnections.containsKey(Integer.valueOf(deviceId))) {
			return AbstractNIHostInterop.globalNIConnections.get(deviceId);
		}


		switch (OperatingSystem.get()) {
			case MAC:
			case MAC_ARM:
				interop = new MacOSNIHostInterop(deviceId, deviceSerial);
				break;

			// FIXME: implement the Windows version of this
			case  WINDOWS:
				interop = null;
				break;

			// We can't communicate with the NIHostIntegrationAgent on other platforms,
			// as NI doesn't support those platforms, yet. Return null.
			default:
				interop = null;
				break;
		}

		// If we just created a global connection, store it.
		if (deviceSerial.isEmpty() && (interop != null)) {
			AbstractNIHostInterop.globalNIConnections.put(deviceId, interop);
		}

		return interop;
	}

	/** Sets the host used to print debug messages. */
	public void setHostForDebug(IHost host) {
		this.host = host;
	}

	/** Prints a debug message, if possible. Same arguments as println(). */
	public void debugPrint(String message, Object ... toFormat) {
		if (this.host == null) {
			return;
		}

		this.host.println(String.format(message, toFormat));
	}


	/**
	 * @return True iff this connection can be used for sending display data.
	 */
	abstract public boolean isUsableForDisplay();


	/**
	 * Sends a request over to the NIHostIntegrationAgent, but does not read a response.
	 *
	 * @param message The raw data to be sent as a request.
	 */
	abstract public void pushRequest(byte [] message);


	/**
	 * Sends a request over to the NIHostIntegrationAgent, and reads a response. Blocking.
	 *
	 * @param message The raw data to be sent as a request.
	 * @return The raw data received as a response; or a 0-byte array if no response was recevied.
	 */
	abstract public byte [] sendRequest(byte [] message);

}
