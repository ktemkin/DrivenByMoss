// Written by Kate Temkin - ktemk.in
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ni.core;

import de.mossgrabers.framework.daw.IHost;

import java.util.List;
import java.util.Arrays;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

import com.sun.jna.*;
import com.sun.jna.ptr.*;


/** {@inheritDocs} */
public class MacOSNIHostInterop extends AbstractNIHostInterop {

	/** The well-known mach port we'll use to communicate with the NIHostIntegrationAgent. */
	private final static String NI_BOOTSTRAP_PORT = "com.native-instruments.NIHostIntegrationAgent";

	/** True iff this is a 'global' connection, rather than a per-device one. */
	private final boolean isGlobalConnection;

	/** The port we'll use to send requests to the NIHostIntegrationAgent. */
	private CFMessagePort requestPort;

	/** The notification port on which we'll receive updates from the NIHostIntegrationAgent. Unused, currently. */
	private CFMessagePort notificationPort;


	public MacOSNIHostInterop(int deviceId, String deviceSerial) throws IOException {
		this.deviceId = deviceId;
		this.isGlobalConnection = deviceSerial.isEmpty();

		// Convert the device's name into an ASCII string.
		this.deviceSerialBytes = Charset.forName("ASCII").encode(deviceSerial);
		this.bootstrapConnections();
	}

	/**
	 * Connects to our NIHostIntegrationAgent port we'll use for bootstrapping.
	 */ 
	private CFMessagePort openPortByName(String name, boolean localPort) {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	
		CFMessagePort port = null;

		// Open up the mach port we'll use for bootstrapping.
		CFStringRef cfName = cfl.CFStringCreateWithCString(CoreFoundationLibrary.kCFAllocatorDefault, name, CoreFoundationLibrary.kCFStringEncodingASCII);

		if (localPort) {
			CFMessagePortContext context = new CFMessagePortContext();
			port = cfl.CFMessagePortCreateLocal(CoreFoundationLibrary.kCFAllocatorDefault, cfName, this::onNotificationReceived, context.getPointer(), null);
		} else {
			port = cfl.CFMessagePortCreateRemote(CoreFoundationLibrary.kCFAllocatorDefault, cfName);
		}

		cfl.CFRelease(cfName);
		return port;
	}

	/**
	 * Bootstraps a per-device connection to the NIHostIntegrationAgent.
	 */
	private void bootstrapConnections() throws IOException {
		byte[] deviceSerial = this.deviceSerialBytes.array();

		// Create a bootstrap port connection, which we'll use to send a handshake.
		CFMessagePort bootstrapPort = this.openPortByName(NI_BOOTSTRAP_PORT, false);

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
		byte [] rawResponse = this.sendOnMachPort(bootstrapPort, rawMessage, true);
		if (rawResponse.length == 0) {
			throw new IOException("NIHostIntegrationAgent did not reply. Failing out.");
		}

		// Interpret our response a variety of ways...
		ByteBuffer response = ByteBuffer.wrap(rawResponse);
		response.order(ByteOrder.LITTLE_ENDIAN);
		if (rawResponse.length == 4) {
			int errorCode = response.getInt();
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

		// ... open our ports...
		this.requestPort = this.openPortByName(requestPortName, false);
		this.notificationPort = this.openPortByName(notificationPortName, true);

		// ... and finish our bootstrapping.
		//this.subscribeToNotifications(notificationPortName);
	}

	/** 
	 * Acknowledges a connection by returning the name of the notification port.
	 */
	private void subscribeToNotifications(String notificationPortName) throws IOException {
		// First, encode the notificationPortName in ASCII.
		ByteBuffer notificationPortAscii = Charset.forName("ASCII").encode(notificationPortName);
		byte[] notificationPortEncoded = notificationPortAscii.array();

		// Build the message we'll use.
		byte [] rawMessage  = new byte[NI_MSG_ACKNOLWEDGE_NOTIFICATION_PORT_LENGTH + notificationPortEncoded.length + 1];
		ByteBuffer messageBuffer = ByteBuffer.allocateDirect(NI_MSG_ACKNOLWEDGE_NOTIFICATION_PORT_LENGTH + notificationPortEncoded.length + 1);
		messageBuffer.order(ByteOrder.LITTLE_ENDIAN);

		messageBuffer.putInt(NI_MSG_ACKNOLWEDGE_NOTIFICATION_PORT);  // The message type.
		messageBuffer.putInt(NI_SUCCESS);                            // Success, since we're ACK'ing.
		messageBuffer.putInt(0);                                     // Padding.
		messageBuffer.putInt(notificationPortEncoded.length);        // The length of the port name that follows.
		messageBuffer.put(notificationPortEncoded);                  // Notification port we're getting.
		messageBuffer.put((byte)0);                                  // Null terminator.

		// Ensure we have a byte array.
		messageBuffer.rewind();
		messageBuffer.get(rawMessage);

		// ... and perform our exchange.
		byte[] result = this.sendRequest(rawMessage);
		if (!this.responseWasSuccess(result)) {
			throw new IOException("NIHostIntegrationAgent did not accept our notification socket.");
		} 

		// Special case: if this is a global connection, request our machine state.
		// This seems necessary to get the NIHostIntegrationAgent to talk to individual devices (?).
		if (this.isGlobalConnection) {
			this.pushRequest(NI_WHOLE_MSG_GET_DEVICE_STATE);		
		}
	}

	/** {@inheritDocs} */
	@Override
	public boolean isUsableForDisplay() {
		// We're usable for display iff we have a requestPort.
		return (this.requestPort != null) && (Pointer.nativeValue(this.requestPort.getPointer()) != 0);
	}


	/** {@inheritDocs} */
	@Override
	public void pushRequest(byte [] data) {
		this.sendOnMachPort(this.requestPort, data, false);
	}


	/** {@inheritDocs} */
	@Override
	public byte[] sendRequest(byte [] data) {
		return this.sendOnMachPort(this.requestPort, data, true);
	}


	/** Handler for notifications received on our notification port. */
	CFDataRef onNotificationReceived(CFMessagePort local, int messageId, CFDataRef data, Pointer info) {
		this.debugPrint("Notification received!");
		return null;
	}

	//
	// Low-level helpers.
	//
	
	private boolean responseWasSuccess(byte[] result) {
		ByteBuffer resultParser = ByteBuffer.wrap(result);
		resultParser.order(ByteOrder.LITTLE_ENDIAN);

		return (resultParser.getInt() == NI_SUCCESS);
	}
	

	/**
	 * Sends simple data on a mach port, and receive the response.
	 */
	private byte [] sendOnMachPort(CFMessagePort port, byte [] message, boolean collectResponse) {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	
		CFDataRef dataToSend = cfl.CFDataCreate(CoreFoundationLibrary.kCFAllocatorDefault, message, message.length);

		try {
			// Send the relevant data, and wait for a response.
			PointerByReference responseDataReference = new PointerByReference();
			int result = cfl.CFMessagePortSendRequest(port, 0, dataToSend, 1000, 1000, collectResponse ? CoreFoundationLibrary.kCFRunLoopDefaultMode : null, responseDataReference);
			if (result != 0) {
				return new byte[0];
			}

			// If we're not collecting a response, return an empty array.
			if (!collectResponse) {
				return new byte[0];
			}
		
			// Otherwise, extract the relevant response.
			return this.convertCFData(new CFDataRef(responseDataReference.getValue()));
		} finally {
			cfl.CFRelease(dataToSend);
		}
	}

	/** Converts a CFDataRef to a Java byte[]. */
	private byte[] convertCFData(CFDataRef data) {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	

		int length = cfl.CFDataGetLength(data);
		Pointer pointer = cfl.CFDataGetBytePtr(data);

		byte [] result = (length > 0) ? pointer.getByteArray(0, length) : new byte[0];
		cfl.CFRelease(data);

		return result;
	}


	//
	// JNA
	//

	public static class CFStringRef extends PointerType {
		public CFStringRef() { super(); }
		public CFStringRef(Pointer pointer) { super(pointer); }
	}
	public static class CFAllocatorRef extends PointerType {
		public CFAllocatorRef() { super(); }
		public CFAllocatorRef(Pointer pointer) { super(pointer); }
	}
	public static class CFMessagePort extends PointerType {
		public CFMessagePort() { super(); }
		public CFMessagePort(Pointer pointer) { super(pointer); }
	}
	public static class CFDataRef extends PointerType {
		public CFDataRef() { super(); }
		public CFDataRef(Pointer pointer) { super(pointer); }
	}


	public static final class CFStringRef_global extends Structure {
		public CFStringRef value;

		CFStringRef_global(Pointer pointer) {
			super();
			useMemory(pointer, 0);
			read();
		}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("value");
		}
	}
	public static final class CFAllocator_global extends Structure {
		public CFAllocatorRef value;

		CFAllocator_global(Pointer pointer) {
			super();
			useMemory(pointer, 0);
			read();
		}

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("value");
		}
	}
	
	public final class CFMessagePortContext extends Structure {
		public int version = 0;
		public Pointer info = null;
		public Pointer retain = null;
		public Pointer release = null;
		public Pointer copyDescription = null;

		@Override
		protected List<String> getFieldOrder() {
			return Arrays.asList("version", "info", "retain", "release", "copyDescription");
		}
	}


	interface CoreFoundationLibrary extends Library {
		CoreFoundationLibrary INSTANCE = (CoreFoundationLibrary)Native.load("CoreFoundation", CoreFoundationLibrary.class);
		NativeLibrary NINSTANCE = NativeLibrary.getInstance("CoreFoundation");

		int kCFStringEncodingASCII = 0x0600;
		int kCFStringEncodingUTF8 = 0x08000100;


		public interface CFMessagePortCallback extends Callback {
			CFDataRef invoke(CFMessagePort local, int messageId, CFDataRef data, Pointer info);
		}


		public static CFAllocatorRef kCFAllocatorDefault = new CFAllocator_global(NINSTANCE.getGlobalVariableAddress("kCFAllocatorDefault")).value;
		public static CFStringRef kCFRunLoopDefaultMode = new CFStringRef_global(NINSTANCE.getGlobalVariableAddress("kCFRunLoopDefaultMode")).value;

		void CFRelease(PointerType toRelease);

		CFDataRef CFDataCreate(CFAllocatorRef allocator, final byte [] data, final int length);
		Pointer CFDataGetBytePtr(CFDataRef data);
		int CFDataGetLength(CFDataRef data);

		CFStringRef CFStringCreateWithCString(CFAllocatorRef allocator, String cStr, int encoding);

		CFMessagePort CFMessagePortCreateRemote(CFAllocatorRef allocator, CFStringRef name);
		CFMessagePort CFMessagePortCreateLocal(CFAllocatorRef allocator, CFStringRef name, CFMessagePortCallback callout, Pointer context, Pointer shouldFreeInfo);
		int CFMessagePortSendRequest(CFMessagePort port, int messageId, CFDataRef data, double sendTimeout, double recvTimeout, CFStringRef replyMode, PointerByReference response);
	}


}
