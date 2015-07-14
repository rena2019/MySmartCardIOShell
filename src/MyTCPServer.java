import java.net.*;
import java.io.*;
import java.util.Arrays;

// Reading from and Writing to a Socket http://docs.oracle.com/javase/tutorial/networking/sockets/readingWriting.html

/*
 simple TCP Server that listens on port
 2012-12-xx started
 2014-11-10 single thread implemented

 */

public class MyTCPServer implements Runnable {

	private static final String HEXES = "0123456789ABCDEF";

	protected boolean isStopped = false;
	protected Thread runningThread = null;
	private ServerSocket serverSocket;
	private boolean alwaysPrompt = false;
	private int serverPort = 8050;
	private MySmartCardIOShell shell;
	private boolean log = false;

	private static String getHex(byte[] raw, int len) {

		if (raw == null) {
			return null;
		}
		final StringBuilder hex = new StringBuilder(2 * raw.length);
		// for ( byte b : raw )
		for (int i = 0; i < len; i++) {
			byte b = raw[i];
			hex.append(HEXES.charAt((b & 0xF0) >> 4))
					.append(HEXES.charAt((b & 0x0F))).append(" ");
		}
		return hex.toString();

	}

	/** Return hex string as byte buffer . */
	public static byte[] fromHexToByte(String convert) {
		byte[] hexarr = hexarr = convert.getBytes();
		int pos = 0;
		String s, hx = "";
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		while (pos < hexarr.length) {
			s = new String(hexarr, pos, 1);
			if ((s.charAt(0) >= '0' && s.charAt(0) <= '9')
					|| (s.charAt(0) >= 'a' && s.charAt(0) <= 'f')
					|| (s.charAt(0) >= 'A' && s.charAt(0) <= 'F')) {
				hx += s;
			}
			pos++;
			if (hx.length() == 2) {
				stream.write(Integer.parseInt(hx, 16));
				hx = "";
			}
		}
		return stream.toByteArray();
	}

	public static void displayAndSend(OutputStream os, byte[] bytes)
			throws IOException {
		System.out.println("   <= " + getHex(bytes, bytes.length));
		os.write(bytes);
		os.flush();
	}

	public MyTCPServer(int port, boolean log, boolean alwaysPrompt) {
		System.out.println(String.format("port: %d, log: %b", port, log));
		this.serverPort = port;
		this.log = log;
		this.alwaysPrompt = alwaysPrompt;
	}

	public void setCallBack(MySmartCardIOShell shell) {
		this.shell = shell;
	}

	@Deprecated
	public void XXXXstartServer() {

		try {
			serverSocket = new ServerSocket(serverPort);
			serverSocket.setSoTimeout(3000);
			System.out.println("listening on port " + serverPort);
		} catch (IOException e) {
			System.err
					.println("Error: Could not listen on port: " + serverPort);
			System.exit(1);
		}

		Socket clientSocket = null;
		try {
			clientSocket = serverSocket.accept();
			// clientSocket.setTcpNoDelay(false);
		}

		catch (IOException e) {
			System.out.println("Accept failed.");
			System.exit(1);
		}
		try {
			System.err.println("connection accepted.");

			// PrintWriter out = new PrintWriter(clientSocket.getOutputStream(),
			// true);
			// DataOutputStream os = new
			// DataOutputStream(clientSocket.getOutputStream());
			OutputStream outputstream = clientSocket.getOutputStream();
			// BufferedReader in = new BufferedReader(new
			// InputStreamReader(clientSocket.getInputStream()));
			// DataInputStream is = new
			// DataInputStream(clientSocket.getInputStream());
			// BufferedInputStream is = new
			// BufferedInputStream(clientSocket.getInputStream());
			InputStream inputstream = clientSocket.getInputStream();

			byte[] buff = new byte[1024];
			int sz = 0;
			boolean bLoop = true;
			do {
				sz = inputstream.read(buff, 0, 4);
				if (sz > 0) {
					System.out.println(" => " + getHex(buff, sz));
					// without user interaction
					if (Arrays.equals(Arrays.copyOf(buff, 4), new byte[] {
							0x00, (byte) 0x21, 0x00, 0x04 })) {
						// ATR
						// read dummy data
						buff = new byte[buff[3]];
						sz = inputstream.read(buff, 0, buff.length);
						if (this.shell != null) {
							byte[] atr = shell.atr();
							byte[] data = new byte[4 + atr.length];
							System.arraycopy(atr, 0, data, 4, atr.length);
							data[2] = (byte) (atr.length >> 8);
							data[3] = (byte) (atr.length & 0xff);
							displayAndSend(outputstream, data);
						} else
							displayAndSend(outputstream, new byte[] { 0,
									(byte) 0x21, 0, 0x04 });
					} else if (Arrays.equals(Arrays.copyOf(buff, 4),
							new byte[] { 0, 0, 0, 0 })) {
						// ATR
						/*
						 * displayAndSend(os, new byte[] { (byte)0x3B,
						 * (byte)0xF9, (byte)0x13, 00, 00, (byte)0x81,
						 * (byte)0x31, (byte)0xFE, (byte)0x45, (byte)0x4A,
						 * (byte)0x43, (byte)0x4F, (byte)0x50, (byte)0x32,
						 * (byte)0x34, (byte)0x32, (byte)0x52, (byte)0x32,
						 * (byte)0xA3 });
						 */
						displayAndSend(outputstream, new byte[] { 0,
								(byte) 0x21, 0, 0x04 });
					} else {
						// apdu
						if (this.shell != null) {
							buff = new byte[buff[3]];
							sz = inputstream.read(buff, 0, buff.length);

							byte[] rapdu = shell.sendAndDisplayApdu(buff);
							byte[] data = new byte[4 + rapdu.length];
							System.arraycopy(rapdu, 0, data, 4, rapdu.length);
							data[0] = 1;
							data[2] = (byte) (rapdu.length >> 8);
							data[3] = (byte) (rapdu.length & 0xff);
							displayAndSend(outputstream, data);

						} else
							displayAndSend(outputstream, new byte[] { 0,
									(byte) 0x21, 0, 0x04 /*
														 * (byte)0x6a,
														 * (byte)0x80
														 */});
					}
				}
			} while (bLoop);

			// TODO check this:
			byte[] message = new byte[] { 1 };
			OutputStream socketOutputStream = clientSocket.getOutputStream();
			socketOutputStream.write(message);

			/*
			 * int1 = in.readLine(); System.out.println(int1); int2 =
			 * in.readLine(); System.out.println("*"+int2);
			 */

			outputstream.close();
			inputstream.close();
			clientSocket.close();
			serverSocket.close();
		} catch (Exception ex) {
			System.err.println(ex.toString());
			System.exit(1);
		}
	}

	public static void main(String[] args) throws IOException {
		System.out.println("MyTCPServer v0.1");
		/*
		 * boolean alwaysPrompt = false; int port=8050;
		 * 
		 * if (args.length > 0) { port = Integer.parseInt(args[0]); if
		 * (args.length > 1) { alwaysPrompt = Boolean.parseBoolean(args[1]); } }
		 * MyTCPServer mytcpserver = new MyTCPServer(port, alwaysPrompt);
		 * mytcpserver.startServer();
		 */
		MyTCPServer server = new MyTCPServer(8050, false, false);
		new Thread(server).start();

		try {
			Thread.sleep(2 * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		MyTCPServer.stopServer(server);

	}// main

	static MyTCPServer startServer(int port, boolean log) {
		MyTCPServer server = new MyTCPServer(port, log, false);
		if (log)
			System.out.println("start Server");
		new Thread(server).start();
		return server;
	}

	static void stopServer(MyTCPServer server) {
		if (server != null) {
			System.out.println("Stopping Server");
			server.stop();
		}
	}

	private void processClientRequest(Socket clientSocket) throws IOException {
		if (log)
			System.out.println("processClientRequest");
		InputStream input = clientSocket.getInputStream();
		OutputStream output = clientSocket.getOutputStream();

		byte[] buff = new byte[1024];
		int sz = 0;
		boolean bLoop = true;
		do {
			try {
				sz = input.read(buff, 0, 4);
				if (sz > 0) {
					System.out.println(" => " + getHex(buff, sz));
					// without user interaction
					if (Arrays.equals(Arrays.copyOf(buff, 4), new byte[] {
							0x00, (byte) 0x21, 0x00, 0x04 })) {
						// ATR
						// read dummy data
						buff = new byte[buff[3]];
						sz = input.read(buff, 0, buff.length);
						if (this.shell != null) {
							byte[] atr = shell.atr();
							byte[] data = new byte[4 + atr.length];
							System.arraycopy(atr, 0, data, 4, atr.length);
							data[2] = (byte) (atr.length >> 8);
							data[3] = (byte) (atr.length & 0xff);
							displayAndSend(output, data);
						} else
							displayAndSend(output, new byte[] { 0, (byte) 0x21,
									0, 0x04 });
					} else if (Arrays.equals(Arrays.copyOf(buff, 4),
							new byte[] { 0, 0, 0, 0 })) {
						// ATR
						/*
						 * displayAndSend(os, new byte[] { (byte)0x3B,
						 * (byte)0xF9, (byte)0x13, 00, 00, (byte)0x81,
						 * (byte)0x31, (byte)0xFE, (byte)0x45, (byte)0x4A,
						 * (byte)0x43, (byte)0x4F, (byte)0x50, (byte)0x32,
						 * (byte)0x34, (byte)0x32, (byte)0x52, (byte)0x32,
						 * (byte)0xA3 });
						 */
						displayAndSend(output, new byte[] { 0, (byte) 0x21, 0,
								0x04 });
					} else {
						// apdu
						if (this.shell != null) {
							buff = new byte[buff[3]];
							sz = input.read(buff, 0, buff.length);

							byte[] rapdu = shell.sendAndDisplayApdu(buff);
							byte[] data = new byte[4 + rapdu.length];
							System.arraycopy(rapdu, 0, data, 4, rapdu.length);
							data[0] = 1;
							data[2] = (byte) (rapdu.length >> 8);
							data[3] = (byte) (rapdu.length & 0xff);
							displayAndSend(output, data);

						} else
							displayAndSend(output, new byte[] { 0, (byte) 0x21,
									0, 0x04 /*
											 * (byte)0x6a, (byte)0x80
											 */});
					}
				} else
				{
					bLoop = false;
				}
			} catch (Exception ex) {
				System.err.println(ex.toString());
				bLoop = false;
			}
		} while (bLoop);

		output.close();
		input.close();
		if (log)
			System.out.println("processClientRequest done");
	}

	private synchronized boolean isStopped() {
		return this.isStopped;
	}

	public synchronized void stop() {
		this.isStopped = true;
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			throw new RuntimeException("Error closing server", e);
		}
	}

	private void openServerSocket() {
		try {
			if (log)
				System.out.println(String.format("open ServerSocket %d", this.serverPort));
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			throw new RuntimeException(String.format("Cannot open port %d",
					this.serverPort));
		}
	}

	@Override
	public void run() {
		synchronized (this) {
			this.runningThread = Thread.currentThread();
		}
		openServerSocket();

		while (!isStopped()) {
			Socket clientSocket = null;
			try {
				if (log)
					System.out.println("waiting for client");
				clientSocket = this.serverSocket.accept();
				if (log)
					System.out.println("connection accepted" + clientSocket.getRemoteSocketAddress().toString());
			} catch (IOException e) {
				if (isStopped()) {
					System.out.println("Server Stopped.");
					return;
				}
				throw new RuntimeException("Error accepting client connection",
						e);
			}
			try {
				processClientRequest(clientSocket);
			} catch (IOException e) {
				// log exception and go on to next request.
				System.err.println("IOException");
			}
		}//while
		if (log)
			System.out.println("Server Stopped.");
	}
}
