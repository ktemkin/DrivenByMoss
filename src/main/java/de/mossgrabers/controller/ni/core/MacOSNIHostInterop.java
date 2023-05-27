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


/** {@inheritDocs} */
public class MacOSNIHostInterop extends AbstractNIHostInterop {
	
	/** The location of libdispatch on macOS; which has a well-known path not in the standard dylib path, for Some Reason(TM). */
	public static final String LIBDISPATCH_PATH = "/usr/lib/system/introspection/libdispatch.dylib";

	/**
	 * Creates a new interface for connecting to the NIHostIntegrationAgent.
	 *
	 * @param The DeviceID for the relevant NI device.
	 * @param The device's serial; or null / empty string for an non-device-specific connection.
	 */
	public MacOSNIHostInterop(int deviceId, String deviceSerial, INIEventHandler eventHandler, IHost host) throws IOException {
		super(deviceId, deviceSerial, eventHandler, host);
	}

	/** The port we'll use to send requests to the NIHostIntegrationAgent. */
	private CFMessagePort requestPort;

	/** The notification port on which we'll receive updates from the NIHostIntegrationAgent. Unused, currently. */
	private CFMessagePort notificationPort;

	/** True iff we've set up the runloop source for our notification callback. */
	private boolean setUpNotificationSource = false;


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
	protected void bootstrapConnections() throws IOException {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	

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
		if (this.isKontrol) {
			messageBuffer.putInt(NI_SOFTWARE_ID_KONTROL);     // The "NI software" that's connecting is KK.
		} else {
			messageBuffer.putInt(NI_SOFTWARE_ID_MASCHINE2);   // The "NI software" that's connecting is Maschine.
		}
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
		this.subscribeToNotifications(notificationPortName);
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


	/** {@inheritDocs} */
	@Override
	protected void pollForNotifications() {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	

		// If this is our first time running in this thread, attach ourselves to the main runloop.
		if (!this.setUpNotificationSource) {
			CFRunLoopSource eventSource = cfl.CFMessagePortCreateRunLoopSource(CoreFoundationLibrary.kCFAllocatorDefault, this.notificationPort, 0);
			if (eventSource.getPointer() == Pointer.NULL) {
				throw new FrameworkException("fatal: event source was NULL");
			}

			cfl.CFRunLoopAddSource(cfl.CFRunLoopGetCurrent(), eventSource, CoreFoundationLibrary.kCFRunLoopCommonModes);
			cfl.CFRelease(eventSource);
			this.setUpNotificationSource = true;
		}
			
		// Run a Grand Central Dispatch runloop for long enough to handle any potential events.
		// We're running for one second, here -- the time we spend here doesn't precisely matter,
		// but it does set the amount of time it may take us to shut down vs the amount of CPU time
		// wasted popping into this thread to re-issue the runloop.
		cfl.CFRunLoopRunInMode(CoreFoundationLibrary.kCFRunLoopDefaultMode, 1, true);
	}


	/** Handler for notifications received on our notification port. */
	CFDataRef onNotificationReceived(CFMessagePort local, int messageId, CFDataRef data, Pointer info) {
		if (data.getPointer() == Pointer.NULL) {
			this.debugPrint("Got a NULL message from the NIHIA?");
			return null;
		}

		// Delegate the raw notification back to the platform-independent code.
		final byte [] notification = this.convertCFData(data, false);
		this.handleNotification(notification);

		return null;
	}

	//
	// Low-level helpers.
	//

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
			return this.convertCFData(new CFDataRef(responseDataReference.getValue()), true);
		} finally {
			cfl.CFRelease(dataToSend);
		}
	}

	/** Converts a CFDataRef to a Java byte[]. */
	private byte[] convertCFData(CFDataRef data, boolean release) {
		CoreFoundationLibrary cfl = CoreFoundationLibrary.INSTANCE;	

		int length = cfl.CFDataGetLength(data);
		Pointer pointer = cfl.CFDataGetBytePtr(data);

		byte [] result = (length > 0) ? pointer.getByteArray(0, length) : new byte[0];

		if (release) {
			cfl.CFRelease(data);
		}

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
	public static class CFRunLoopSource extends PointerType {
		public CFRunLoopSource() { super(); }
		public CFRunLoopSource(Pointer pointer) { super(pointer); }
	}
	public static class CFRunLoop extends PointerType {
		public CFRunLoop() { super(); }
		public CFRunLoop(Pointer pointer) { super(pointer); }
	}
	public static class DispatchQueue extends PointerType {
		public DispatchQueue() { super(); }
		public DispatchQueue(Pointer pointer) { super(pointer); }
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


	/** Interface to the OSX CoreFounation. */
	interface CoreFoundationLibrary extends Library {
		CoreFoundationLibrary INSTANCE = (CoreFoundationLibrary)Native.load("CoreFoundation", CoreFoundationLibrary.class);
		NativeLibrary NINSTANCE = NativeLibrary.getInstance("CoreFoundation");

		//
		// Global constants.
		//
		//
		int kCFStringEncodingASCII = 0x0600;
		int kCFStringEncodingUTF8 = 0x08000100;

		int kCFRunLoopTimedOut = 3;
		int kCFRunLoopRunHandledSource = 4;

		public static CFAllocatorRef kCFAllocatorDefault = new CFAllocator_global(NINSTANCE.getGlobalVariableAddress("kCFAllocatorDefault")).value;
		public static CFStringRef kCFRunLoopDefaultMode = new CFStringRef_global(NINSTANCE.getGlobalVariableAddress("kCFRunLoopDefaultMode")).value;
		public static CFStringRef kCFRunLoopCommonModes = new CFStringRef_global(NINSTANCE.getGlobalVariableAddress("kCFRunLoopCommonModes")).value;

		//
		// Data translation.
		//
		CFStringRef CFStringCreateWithCString(CFAllocatorRef allocator, String cStr, int encoding);
		CFDataRef CFDataCreate(CFAllocatorRef allocator, final byte [] data, final int length);
		Pointer CFDataGetBytePtr(CFDataRef data);
		int CFDataGetLength(CFDataRef data);
		void CFRelease(PointerType toRelease);

		//
		// Mach-port messaging
		//
		CFMessagePort CFMessagePortCreateRemote(CFAllocatorRef allocator, CFStringRef name);
		CFMessagePort CFMessagePortCreateLocal(CFAllocatorRef allocator, CFStringRef name, CFMessagePortCallback callout, Pointer context, Pointer shouldFreeInfo);
		int CFMessagePortSendRequest(CFMessagePort port, int messageId, CFDataRef data, double sendTimeout, double recvTimeout, CFStringRef replyMode, PointerByReference response);
		CFRunLoopSource CFMessagePortCreateRunLoopSource(CFAllocatorRef alloactor, CFMessagePort port, int order);

		public interface CFMessagePortCallback extends Callback {
			CFDataRef invoke(CFMessagePort local, int messageId, CFDataRef data, Pointer info);
		}

		//
		// Runloop Management
		//
		CFRunLoop CFRunLoopGetCurrent();
		int CFRunLoopRunInMode(CFStringRef mode, double seconds, boolean returnAfterSourceHandled);
		void CFMessagePortSetDispatchQueue(CFMessagePort port, DispatchQueue dispatchQueue);
		void CFRunLoopAddSource(CFRunLoop runLoop, CFRunLoopSource source, CFStringRef modeName);

	}


}
