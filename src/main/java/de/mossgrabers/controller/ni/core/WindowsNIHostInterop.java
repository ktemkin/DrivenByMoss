// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.core;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.FrameworkException;

import java.util.List;
import java.util.Arrays;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import com.sun.jna.*;
import com.sun.jna.ptr.*;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;


/** {@inheritDocs} */
public class WindowsNIHostInterop extends AbstractNIHostInterop {

	/** The port we'll use to send requests to the NIHostIntegrationAgent. */
	private WinNT.HANDLE requestPort;


	/**
	 * Creates a new interface for connecting to the NIHostIntegrationAgent.
	 *
	 * @param The DeviceID for the relevant NI device.
	 * @param The device's serial; or null / empty string for an non-device-specific connection.
	 */
	public WindowsNIHostInterop(int deviceId, String deviceSerial, INIEventHandler eventHandler, IHost host) throws IOException {
		super(deviceId, deviceSerial, eventHandler, host);
	}

	/**
	 * Opens a named pipe by name.
	 */ 
	private WinNT.HANDLE openPortByName(String name) throws IOException {
		String pipeName = "\\\\.\\pipe\\" + name;

		// First, open the pipe itself...
		WinNT.HANDLE file = Kernel32.INSTANCE.CreateFile(pipeName, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null, WinNT.OPEN_EXISTING, WinNT.FILE_ATTRIBUTE_NORMAL, null);
		if (file == WinNT.INVALID_HANDLE_VALUE) {
			throw new IOException("Failed to open WinNT handle for named pipe " + pipeName + "!");
		}

		// ... and set it up.
		IntByReference dwMode = new IntByReference(WinNT.PIPE_READMODE_MESSAGE);
		boolean success = Kernel32.INSTANCE.SetNamedPipeHandleState(file, dwMode, null, null);
		if (!success) {
			throw new IOException("Failed to configure named pipe!");
		}

		return file;
	}


	/**
	 * Creates a named pipe with the given name.
	 */ 
	private WinNT.HANDLE createPortWithName(String name) throws IOException {
		String pipeName = "\\\\.\\pipe\\" + name;

	//Pipe->hPipeInst = CreateNamedPipeA(
	//	PortName,            // pipe name 
	//	PIPE_ACCESS_DUPLEX |     // read/write access 
	//	FILE_FLAG_OVERLAPPED,    // overlapped mode 
	//	PIPE_TYPE_MESSAGE |      // message-type pipe 
	//	PIPE_READMODE_MESSAGE |  // message-read mode 
	//	PIPE_WAIT,               // blocking mode 
	//	1,               // number of instances 
	//	BUFSIZE,   // output buffer size 
	//	BUFSIZE,   // input buffer size 
	//	PIPE_TIMEOUT,            // client time-out 
	//	NULL);                   // default security attributes 

		// First, open the pipe itself...
		WinNT.HANDLE file = Kernel32.INSTANCE.CreateNamedPipe(pipeName, WinNT.PIPE_ACCESS_DUPLEX, WinNT.PIPE_TYPE_MESSAGE | WinNT.PIPE_READMODE_MESSAGE | WinNT.PIPE_WAIT, 1, 1024, 1024, 1000, null);
		if (file == WinNT.INVALID_HANDLE_VALUE) {
			throw new IOException("Failed to create WinNT handle for named pipe " + pipeName + "!");
		}

		return file;
	}

	/**
	 * Bootstraps a per-device connection to the NIHostIntegrationAgent.
	 */
	@Override
	protected void bootstrapConnections() throws IOException {
		byte[] deviceSerial = this.deviceSerialBytes.array();

		// Create a bootstrap port connection, which we'll use to send a handshake.
		WinNT.HANDLE bootstrapPort = this.openPortByName(NI_BOOTSTRAP_PORT);

		// Build the message we'll use.
		byte [] rawMessage  = new byte[NI_MSG_HANDSHAKE_LENGTH + deviceSerial.length + 1];
		ByteBuffer messageBuffer = ByteBuffer.allocateDirect(NI_MSG_HANDSHAKE_LENGTH + deviceSerial.length + 1);
		messageBuffer.order(ByteOrder.LITTLE_ENDIAN);

		if (deviceSerial.length == 0) {
			messageBuffer.putInt(NI_MSG_HANDSHAKE);           // Connect to the server, but not to specific hardware.
		}
		else {
			messageBuffer.putInt(NI_MSG_CONNECT);             // Connect to a port for a specific piece of hardware.
		}
															  //
		messageBuffer.putInt(this.deviceId);                  // The device type.
		messageBuffer.putInt(NI_SOFTWARE_ID_MASCHINE2);       // The "NI software" that's connecting.
		messageBuffer.putInt(NI_HEADER_CONSTANT);             // Unknown. Possibly protocol version?
		messageBuffer.putInt(deviceSerial.length + 1);        // The length of the serial number that follows, plus a NULL.
		
		if (deviceSerial.length != 0) {
		  messageBuffer.put(deviceSerial);                    // The serial number of the device we want to control, if any.
	  	  messageBuffer.put((byte)0);					      // A null terminator for after the device serial.
	    }

		// Ensure we have a byte array.
		messageBuffer.rewind();
		messageBuffer.get(rawMessage);

		// ... and perform our exchange.
		byte [] rawResponse = this.sendOnPort(bootstrapPort, rawMessage, true);
		if (rawResponse.length == 0) {
			throw new IOException("NIHostIntegrationAgent did not reply. Failing out.");
		}

		// Interpret our response a variety of ways...
		ByteBuffer response = ByteBuffer.wrap(rawResponse);
		response.order(ByteOrder.LITTLE_ENDIAN);
		if (rawResponse.length == 4) {
			throw new IOException("NIHostIntegrationAgent reports an error. Failing out.");
		}

		// ... so we can extract our target data.
		int checkVal = response.getInt();
		int requestPortLength = response.getInt();
		
		if (checkVal != NI_SUCCESS) {
			throw new IOException("Failed to communicate!");
		}

		// Finally, extract the core port name we need...
		CharBuffer responseChars = Charset.forName("ASCII").decode(response);
		String requestPortName   = responseChars.subSequence(0, requestPortLength - 1).toString();

		int notificationPortLength = response.getInt(8 + requestPortLength);
		String notificationPortName = responseChars.subSequence(requestPortLength + 4, requestPortLength + 4 + notificationPortLength - 1).toString();

		//// ... open our ports...
		this.requestPort = this.openPortByName(requestPortName);
		this.createPortWithName(notificationPortName);

		// ... and finish our bootstrapping.
		this.subscribeToNotifications(notificationPortName);
	}


	/** {@inheritDocs} */
	@Override
	public boolean isUsableForDisplay() {
		return (this.requestPort != WinNT.INVALID_HANDLE_VALUE);
	}


	/** {@inheritDocs} */
	@Override
	public void pushRequest(byte [] data) {
		this.sendOnPort(this.requestPort, data, false);
	}


	/** {@inheritDocs} */
	@Override
	public byte[] sendRequest(byte [] data) {
		return this.sendOnPort(this.requestPort, data, true);
	}

	//
	// Low-level helpers.
	//
	
	/**
	 * Sends simple data on a named pipe, and receive the response.
	 * Cannot read more than 1024B, currently; but NI never sends that much.
	 */
	private byte [] sendOnPort(WinNT.HANDLE port, byte [] message, boolean collectResponse) {
		int retries = 10;
		boolean sent = false;

		if (port == WinNT.INVALID_HANDLE_VALUE) {
			throw new FrameworkException("Internal consistency: trying to send on an invalid handle");
		}
		if (port == null) {
			throw new FrameworkException("Internal consistency: trying to send on an uninitialized handle");
		}

		// Optimization: if we're not looking for a response, don't read one.
		if (!collectResponse) {
			IntByReference actualWriteSize = new IntByReference(0);
			sent = Kernel32.INSTANCE.WriteFile(port, message, message.length, actualWriteSize, null);

			if (!sent || (actualWriteSize.getValue() != message.length)) {
				this.debugPrint("Failed to push message!");
			}
			return null;
		}

		// Windows will often fail with "pipe busy, try again". We'll keep reading from the pipe
		// until we either get the data we want, or get a real error.
		byte[] response = new byte[1024];
		IntByReference bytesRead = new IntByReference(0);

		while (!sent && retries > 0) {
			sent = Kernel32.INSTANCE.TransactNamedPipe(port, message, message.length, response, response.length, bytesRead, null); 
			if (sent) {
				break;
			}

			// If we failed to transact on the pipe, check the error.
			// If it's anything other than "try again", fail out.
			int result = Kernel32.INSTANCE.GetLastError();
			if (result != Kernel32.ERROR_PIPE_BUSY) {
				this.debugPrint(String.format("Failed to send on %s pipe (%d)", port == this.requestPort ? "request" : "bootstrap", result));
				return null;
			}

			// Give the pipe a little bit to be less busy.
			try {
				Thread.sleep(100);
			}
			catch(InterruptedException ex) {}

			--retries;
		}

		// If we stil haven't transacted, fail out, since we've stalled things for a second, already.
		if (!sent) {
			this.debugPrint("Retried 10 times, but no successful pipe write!");
			return null;
		}

		return Arrays.copyOfRange(response, 0, bytesRead.getValue());
	}

	@Override
	void pollForNotifications() {
		// FIXME(ktemkin): implement our server for Windows
	}

}
