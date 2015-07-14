import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.smartcardio.*;

//import net.sourceforge.gpj.cardservices.GlobalPlatformService;

//import com.licel.jcardsim.smartcardio.JCardSimProvider;

import java.util.Scanner;

/*

 2011-xx-xx started
 2012-08-01 dumpMifare/NDEF added
 2013-02-20 clean up / merge / help added
 2013-02-21 atr, ctrl added
 2013-03-03 fix: property "line.separator", linux: start with java -Dsun.security.smartcardio.library=/lib/x86_64-linux-gnu/libpcsclite.so.1 -jar MySmartCardIO.jar
 2013-04-15 log9000 commands added
 2013-05-25 gpj commands added, select command added
 2013-06-30 com port added
 2013-07-21 errors from sw.txt file
 2014-03-17 linux usage added
 2014-08-28 /chkapdu
 2014-11-10 /startserver, /stopserver added
 2014-12-13 /logfile added
 2015-07-14 github commit

 TODO: 
 check for java 1.8
 display hexdump
 re-add jCardSim
 re-add MyShell (refimp handling)
 connect("DIRECT"), control command http://stackoverflow.com/questions/11683885/nfc-card-reader-acr-122-incompatible-with-android-4-1-jelly-beans
 fix no reader exception

 test log9000 apdu mit c-adpu r-adpu(>80 bytes)

 gpj options
 -sdaid <aid>      Security Domain AID, default a000000003000000
 -keyset <num>     use key set <num>, default 0
 -mode <apduMode>  use APDU mode, CLR, MAC, or ENC, default CLR
 -enc <key>        define ENC key, default: 40..4F
 -mac <key>        define MAC key, default: 40..4F
 -kek <key>        define KEK key, default: 40..4F

 /*example

 http://download.oracle.com/javase/6/docs/jre/api/security/smartcardio/spec/javax/smartcardio/package-summary.html

 Problem: Access restriction: The method getDefault() from the type TerminalFactory is not accessible due to restriction on required library C:\Program Files\Java\jre6\lib\rt.jar
 Fix: http://lkamal.blogspot.com/2008/09/eclipse-access-restriction-on-library.html
 Windows -> Preferences -> Java -> Compiler -> Errors/Warnings: "Forbidden reference (access rules)" -> Warning


 Omnikey Contactless Smart Card Readers DEVELOPER GUIDE November 2010 http://www.hidglobal.com/documents/ok_contactless_developer_guide_an_en.pdf
 DESFire  lesen: 4.1.2 Example: Read Card Data through ISO 7816-4 Framed APDU
 90 BD 00 00 07 11 00 00 00 00 02 00 00 http://sebastianschaper.net/index.php/archives/13

 SDIO http://www.scmmicro.com/support/download/SDI010.MANUAL.V1.13.pdf

 http://www.acs.com.hk/drivers/eng/TDS_DESF_EV1.pdf 

 5.1 MIFARE Card: GetUID, LoadKey, Authenticate, Verify, Update Binary, Read Binary, 
 [PCSC_2.01] PC/SC Workgroup Specifications 2.01
 http://www.pcscworkgroup.com/specifications/specdownload.php
 http://www.pcscworkgroup.com/specifications/files/pcsc1-10v2.01.10.zip
 pcsc3_v2.01.09.pdf 3.2.2.1.3 Get Data Command

 */

@SuppressWarnings("restriction")
public class MySmartCardIOShell {

	private TerminalFactory factory;
	private List<CardTerminal> terminals;
	private CardTerminal terminal;
	private Card card;
	private CardChannel channel;
	private Properties propSWs = new Properties();
	private String context = "-";
	private final String LINE_PREFIX = "   ";
	private boolean readFromFile = false;
	private String scriptFile = null;
	private BufferedReader br;
	private MyTCPServer server = null;
	private boolean autoclose = true;
	private File logfile = null;

	public static void main(String[] args) {
		String libpath = System.getProperty("sun.security.smartcardio.library");
		if (libpath != null)
			System.out.println("Smartcardlibrary: " + libpath);
		MySmartCardIOShell shell = new MySmartCardIOShell();
		if (args.length > 1 && args[0].equals("-f")) {
			shell.fromFile(args[1]);
		}
		shell.doShell(args);
	}// main

	// constructor
	public MySmartCardIOShell() {
		readSWResource();
	}

	public void fromFile(String file) {
		scriptFile = file;
		readFromFile = true;
		if (!new File(file).exists()) {
			System.err.println(String.format("file %s not found", file));
			System.exit(-1);
		}
		try {
			br = new BufferedReader(new FileReader(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public void doShell(String[] args) {
		boolean shellLoop = true;
		Scanner input = new Scanner(System.in).useDelimiter(System
				.getProperty("line.separator"));
		String line = "";
		displayUsage();
		initTerminals();
		do {
			System.out.print(context);
			if (readFromFile) {
				// from file
				try {
					line = br.readLine();
					if (line != null)
						log(line);
					else
						readFromFile = false;
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				// from prompt
				// get next command
				line = input.next();
			}
			if (line != null) {
				String[] cmd_args = line.split(" ");
				if (line.startsWith("/term"))
					openTerminal(cmd_args);
				else if (line.startsWith("/interactive")) {
					readFromFile = false;
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					autoclose = false;
				}
				else if (line.startsWith("/echo"))
					echo(cmd_args);
				else if (line.startsWith("/startserver"))
					startServer(cmd_args);
				else if (line.startsWith("/stopserver"))
					stopServer(cmd_args);
				else if (line.startsWith("/checkapdu"))
					checkApdu(cmd_args);
				else if (line.startsWith("/help"))
					displayHelp(cmd_args);
				else if (line.startsWith("/jcop_id") || line.startsWith("/id"))
					identify();
				else if (line.startsWith("/ctrl"))
					ctrl();
				else if (line.startsWith("/atr"))
					atr();
				else if (line.startsWith("/close"))
					close();
				else if (line.startsWith("/log9000"))
					log9000(cmd_args);
				else if (line.equals("/mifare"))
					dumpMifareWithSCL011(true);
				else if (line.equals("/desfire"))
					readDesfire();
				else if (line.startsWith("/file"))
					sendfile(cmd_args);
				else if (line.startsWith("/sel")) {
					select(line);
				}
				else if (line.startsWith("/logfile")) {
					logfile(cmd_args);
				}
				// gpj commands
				/*else if (line.equals("ls") || (line.startsWith("/card"))) {
					gpj_ls();
				} else if (line.startsWith("install ")) {
					gpj_install(line);
				} else if (line.startsWith("upload ")) {
					gpj_upload(line);
				} else if (line.startsWith("delete ")) {
					gpj_delete(line);
				}*/ else if (line.startsWith("/send")) {
					line = line.substring(line.indexOf(" "));
					sendAndDisplayApdu(Util.fromHexToByte(line.replaceAll(" ",
							"").replace("\"", "")));
				}else if(line.startsWith("/quit") || line.startsWith("quit")) {
					shellLoop = false;
				}
				else if (line.startsWith("/list-r"))
					listReaders();
				else if (!line.equals("")) {
					int idx = line.length()-1;
					if (line.indexOf(" ")>=0)
						idx = line.indexOf(' ');
					String filename = line.substring(0, idx+1) + ".txt";
					if (new File(filename).exists()) {
						fromFile(filename);
						autoclose = false;
					}
					else {
						//digits -> send apdu
						sendAndDisplayApdu(Util.fromHexToByte(line.replaceAll(" ",
								"")));
					}
				}
			} else if (autoclose)
				shellLoop = false;
		} while (shellLoop);
		close();
	}

	private void logfile(String[] cmd_args) {
		logfile = new File(cmd_args[1]);
		
	}

	private void echo(String[] cmd_args) {
		log(cmd_args[1]);
		
	}

	//start server on TCP port
	private void startServer(String[] cmd_args) {

		/*
		MyTCPServer mytcpserver;
		if (cmd_args.length > 1)
			mytcpserver = new MyTCPServer(Integer.parseInt(cmd_args[1]), false);
		else
			mytcpserver = new MyTCPServer(8050, false);
		mytcpserver.setCallBack(this);
		mytcpserver.startServer();
		*/
		int port = 8050;
		boolean log = false;
		if (cmd_args.length > 1)
			port = Integer.parseInt(cmd_args[1]);
		if (cmd_args.length > 2)
			log = Boolean.parseBoolean(cmd_args[2]);
		server = MyTCPServer.startServer(port, log);
		server.setCallBack(this);
	}
	private void stopServer(String[] cmd_args) {
		if (server != null)
			MyTCPServer.stopServer(server);
	}

	/**
	 * Checks the card for supported CLA, INS
	 * 
	 * @param cmd_args
	 */
	private void checkApdu(String[] cmd_args) {
		try {
			//
			List<Byte> supportedCmd = new ArrayList<Byte>();
			String apduTemplate = "??000000";
			String responseTemplate = "6D00";

			if (cmd_args.length > 1)
				apduTemplate = cmd_args[1];
			if (cmd_args.length > 2)
				responseTemplate = cmd_args[2];
			for (int i = 0; i < 256; i++) {
				byte[] cmd = Util.fromHexToByte(apduTemplate.replace("??",
						String.format("%02X", i)));
				byte[] response = sendAndDisplayApdu(cmd);
				// 6E00 unsupported CLA?
				if (response != null
						&& response[0] != Util.fromHexToByte(responseTemplate)[0]
						&& response[1] != Util.fromHexToByte(responseTemplate)[1]) {
					supportedCmd.add((byte) i);
				}
			}
			log("supported apdus: apdu template=" + apduTemplate
					+ ", response template=" + responseTemplate);
			for (int i = 0; i < supportedCmd.size(); i++) {
				log(String.format("%02X",
						supportedCmd.get(i) & 0xFF) + " ");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private void listReaders() {
		for (int i = 0; i < terminals.size(); i++) {
			try {
				System.out
						.println(i
								+ ": "
								+ terminals.get(i).getName()
								+ ", "
								+ terminals.get(i).getClass()
										.getCanonicalName()
								+ ", card present: "
								+ terminals.get(i).isCardPresent());
			} catch (CardException e) {
				e.printStackTrace();
			}
		}
	}

	private void initTerminals() {
		try {
			factory = TerminalFactory.getDefault();
			/*
				// TODO
				// settings for applet
				String TEST_APPLET_AID = "010203040506070809";
				System.setProperty(
						"com.licel.jcardsim.smartcardio.applet.0.AID",
						TEST_APPLET_AID);
				System.setProperty(
						"com.licel.jcardsim.smartcardio.applet.0.Class",
						"com.licel.jcardsim.samples.HelloWorldApplet");

				// jCardSim add to Security Provider
				if (Security.getProvider("jCardSim") == null) {
					JCardSimProvider provider = new JCardSimProvider();
					Security.addProvider(provider);
				}
				// get jCardSim factory
				factory = TerminalFactory.getInstance("jCardSim", null);
			}
			// log(factory.getProvider().getName());
			// TerminalFactory factory = TerminalFactory.getInstance("PC/SC",
			// null, "SunPCSC");
			// log(factory);
			 */

			terminals = factory.terminals().list();
		} catch (Exception ex) {
			System.err.println(ex.toString());
		}
	}

	public void openTerminal(String[] cmd_args) {
		try {
			if (terminals.size() == 0)
				displayLibraryUsage();
			else {
				for (int i = 0; i < terminals.size(); i++) {
					log(i + ": " + terminals.get(i).getName()
							+ ", "
							+ terminals.get(i).getClass().getCanonicalName()
							+ ", card present: "
							+ terminals.get(i).isCardPresent());
				}
			}
			// log("Terminals: " + terminals);
			// Scanner input=new Scanner(System.in);
			// System.out.print("?");
			// String line=input.next();
			// get the first terminal
			// cmd_args = terminals.get(Integer.parseInt(line));
			// use first reader with card present
			for (int i = 0; i < terminals.size(); i++) {
				if (terminals.get(i).isCardPresent())
					terminal = terminals.get(i);
			}
			establishConnection();
		} catch (CardException cardEx) {
			/*
			 * Windows can give use a sun.security.smartcardio.PCSCException
			 * SCARD_E_NO_READERS_AVAILABLE here in case no card readers are
			 * connected to the system.
			 */
			cardEx.printStackTrace();
			// TODO
		} catch (Exception ex) {
			ex.printStackTrace();
			// TODO
		}
	}

	private void select(String line) {
		String parm = line.substring(line.indexOf(" ") + 1);
		if (parm.indexOf("|") >= 0) {
			// TODOif (parm[1].endsWith("|"))
			// parm[1] = parm[1].substring(0, parm[1].length() - 1); //remove
			// end "|"
			parm = parm.substring(1);
			// chars to hex
			parm = Util.byteArrayToHex(parm.getBytes(), "   ");
		}
		parm = parm.replaceAll(" ", "");
		String apdu = "00 A4 04 00 " + String.format("%02X", parm.length() / 2)
				+ " " + parm;
		sendAndDisplayApdu(Util.fromHexToByte(apdu));
	}
	
	private void log(String info) {
		System.out.println(info);
		if (logfile != null) {
			try {
				FileWriter outFile = new FileWriter(logfile, true);
				PrintWriter out = new PrintWriter(outFile);
				out.println(info);
				out.close();
				outFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	private void logerr(String err) {
		System.err.println(err);
		if (logfile != null) {
			try {
				FileWriter outFile = new FileWriter(logfile);
				PrintWriter out = new PrintWriter(outFile);
				out.println(err);
				out.close();
				outFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/* TODO GPJ
	private void gpj_delete(String line) {
		try {
			String[] parm = line.split(" ");
			parm[0] = "-delete";
			GlobalPlatformService.main(parm);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void gpj_upload(String line) {
		try {
			String[] parm = line.split(" ");
			parm[0] = "-load";
			GlobalPlatformService.main(parm);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	*/

	/*
	 * -install install applet: -applet <aid> applet AID, default: take all AIDs
	 * from the CAP file -package <aid> package AID, default: take from the CAP
	 * file -priv <num> privileges, default 0 -param <bytes> install parameters,
	 * default: C900
	 */
	/* TODO GPJ
	private void gpj_install(String line) {
		try {
			String[] parm = line.split(" ");
			parm[0] = "-install";
			GlobalPlatformService.main(parm);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void gpj_ls() {
		try {
			GlobalPlatformService.main(new String[] { "-list" });
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	*/

	/*
	 * -load <cap>
	 */

	private void sendfile(String[] cmd_args) {
		FileWriter outFile = null;
		PrintWriter out = null;
		try {
			BufferedReader br = new BufferedReader(new FileReader(cmd_args[1]));

			if (cmd_args.length == 3) {
				outFile = new FileWriter(cmd_args[2]);
				out = new PrintWriter(outFile);
			}

			String line = br.readLine();

			while (line != null) {
				line = br.readLine();
				byte[] response = sendAndDisplayApdu(Util.fromHexToByte(line));
				if (cmd_args.length == 3) {
					out.println("log9000 apdu "
							+ line.replaceAll(" ", "")
							+ " "
							+ Util.byteArrayToHex(response, "").replaceAll(" ",
									""));
				}
			}
		} catch (Exception ex) {
			System.err.println(ex.toString());
		} finally {
			if (cmd_args.length == 3) {
				out.close();
				try {
					outFile.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}

	private void log9000Help() {
		log("log9000 [list [ <file> ]]\r\n"
				+ "log9000 clear\r\n" + "log9000 def_sw <sw>\r\n"
				+ "log9000 version\r\n" + "log9000 apdu <capdu> <rapdu>\r\n"
				+ "log9000 apdu clear\r\n" + "log9000 logon | logoff\r\n"
				+ "log9000 cron | croff\r\n");
	}

	/*
	 * /send 00600000 readLog index=0 /send 00600001 readLog index=1 /send
	 * 00610000 get log index (max index for readLog)
	 * 
	 * log9000 list <filename> log9000 clear log9000 def_sw <sw> log9000 version
	 * log9000 apdu <capdu> <rapdu> log9000 logon | logoff log9000 cron | croff
	 */
	private void log9000(String[] cmd_args) {
		try {

			byte[] response;
			if (cmd_args.length >= 1 && cmd_args.length <= 3
					&& cmd_args[1].equals("list")) {
				// list
				FileWriter outFile = null;
				PrintWriter out = null;
				if (cmd_args.length == 3) {
					outFile = new FileWriter(cmd_args[2]);
					out = new PrintWriter(outFile);
				}
				byte[] APDU_GET_LOG_INDEX = new byte[] { (byte) 00,
						(byte) 0x61, (byte) 00, (byte) 00, };
				response = sendAndDisplayApdu(APDU_GET_LOG_INDEX);
				// log("<= " + Util.byteArrayToHex(response));
				if (response.length == 4) {
					int cnt = (((byte) response[0] & 0xFF) << 8 | ((byte) response[1] & 0xFF));
					// List<byte[]> arr = new ArrayList<byte[]>();
					for (int i = 0; i < cnt; i++) {
						byte[] res = sendAndDisplayApdu(new byte[] { 0x00,
								(byte) 0x60, (byte) ((i >> 8) & 0xFF),
								(byte) (i % 256) });
						if (cmd_args.length == 3)
							out.print(Util.getHexDump(res, 0, res.length - 2)
									+ " 00\r\n");
						// arr.add(res);

					}
					if (cmd_args.length == 3) {
						out.close();
						outFile.close();
					}
				}
			} else if (cmd_args.length == 2 && cmd_args[1].equals("clear")) {
				sendAndDisplayApdu(new byte[] { 0x00, 0x40, 0x03, 0x00 });
			} else if (cmd_args.length >= 2 && cmd_args[1].equals("def_sw")) {
				// display default sw
				response = sendAndDisplayApdu(new byte[] { 0x00, 0x42, 0x00,
						0x00 });
				if (response.length == 4) {
					byte[] def_sw = new byte[2];
					System.arraycopy(response, 0, def_sw, 0, 2);
					log("default sw: "
							+ Util.byteArrayToHex(def_sw, ""));
				}
				if (cmd_args.length == 3) {
					byte[] b = new byte[] { 0x00, 0x41, 0x00, 0x00 };
					System.arraycopy(Util.fromHexToByte(cmd_args[2]), 0, b, 2,
							2);
					sendAndDisplayApdu(b);
				}
			} else if (cmd_args.length == 2 && cmd_args[1].equals("version")) {
				response = sendAndDisplayApdu(new byte[] { 0x00, 0x10, 0x00,
						0x00 });
				if (response.length == 3) {
					log(String.format("version: 0x%X",
							(byte) response[0]));
				}
			} else if ((cmd_args.length == 4 || cmd_args.length == 3)
					&& cmd_args[1].equals("apdu")) {
				if (cmd_args.length == 3 && cmd_args[2].equals("clear")) {
					// clear
					sendAndDisplayApdu(new byte[] { 0x00, 0x40, 0x04, 0x00 });
					return;
				}
				byte[] capdu = Util.fromHexToByte(cmd_args[2]);
				byte[] rapdu = Util.fromHexToByte(cmd_args[3]);
				byte[] b = new byte[4 + 1 + 1 + capdu.length + 1 + rapdu.length];
				System.arraycopy(new byte[] { 0x00, 0x50, 0x01, 0x00 }, 0, b,
						0, 4);
				b[4] = (byte) ((2 + capdu.length + rapdu.length) & 0xff);
				b[5] = (byte) (capdu.length & 0xff);
				System.arraycopy(capdu, 0, b, 6, capdu.length);
				b[6 + capdu.length] = (byte) (rapdu.length & 0xff);
				System.arraycopy(rapdu, 0, b, 7 + capdu.length, rapdu.length);
				response = sendAndDisplayApdu(b);
			} else if (cmd_args.length == 2 && cmd_args[1].startsWith("log")) {
				byte[] b = new byte[] { 0x00, 0x40, 0x02, 0x00 };
				if (cmd_args[1].equals("logon")) {
					b[3] = 0x07; // on
				} else
					b[3] = 0x00; // off
				response = sendAndDisplayApdu(b);
			} else if (cmd_args.length == 2 && cmd_args[1].startsWith("cr")) {
				byte[] b = new byte[] { 0x00, 0x40, 0x04, 0x01 }; // cr_activated
																	// = off
				if (cmd_args[1].equals("cron"))
					b[3] = 0x02; // on
				response = sendAndDisplayApdu(b);
			} else {
				System.err.println("??? unknown options: " + cmd_args[1]);
			}
		} catch (Exception ex) {
			log(ex.toString());
		}
	}

	public void close() {
		try {
			if (card != null)
				card.disconnect(false);
		} catch (CardException e) {
			e.printStackTrace();
		}
		card = null;
		channel = null;
		context = "-";
		if (server != null)
			server.stop();
	}

	private void ctrl() {
		byte[] response = null;
		byte[] command = new byte[] { (byte) 0xff, (byte) 0x00, (byte) 0x40,
				(byte) 0xd0, (byte) 0x04, (byte) 0x05, (byte) 0x05,
				(byte) 0x02, (byte) 0x01 };
		int controlCode = 0x310000 + 3500 * 4;
		try {
			if (card != null)
				card.disconnect(false);
			card = terminal.connect("DIRECT");
			response = card.transmitControlCommand(controlCode, command);
			log(Util.byteArrayToHex(response, LINE_PREFIX));

		} catch (CardException e) {
			e.printStackTrace();
		}
	}

	public byte[] atr() {
		if (card != null)
			try {
				card.disconnect(true);
				return establishConnection();
			} catch (CardException e) {
				e.printStackTrace();
			}
		return new byte[] {};
	}

	private byte[] establishConnection() throws CardException {
		// establish a connection with the card
		card = terminal.connect("*");
		log("protocol: " + card.getProtocol() + ", ATR: "
				+ Util.byteArrayToHex(card.getATR().getBytes(), "") + " "
				+ new String(card.getATR().getHistoricalBytes()));
		channel = card.getBasicChannel();
		context = ">";
		return card.getATR().getBytes();
	}

	public byte[] sendAPDU(byte[] apdu) {
		try {
			ResponseAPDU r = channel.transmit(new CommandAPDU(apdu));
			return r.getBytes();
		} catch (Exception e) {
			logerr(e.toString());
		}
		return new byte[] { 0x6F, 0x00 };//TODO log
	}

	public byte[] sendAndDisplayApdu(byte[] apdu) {
		try {
			log("=> " + Util.byteArrayToHex(apdu, LINE_PREFIX));
			byte[] response = sendAPDU(apdu);
			log(String.format("<= %s",
					Util.byteArrayToHex(response, LINE_PREFIX).toUpperCase())
					+ displayStatusword(response));
			return response;
		} catch (Exception ex) {
			logerr(ex.toString());
			
		}
		return new byte[] { 0x6F, 0x01 };//TODO log
	}

	private void displayUsage() {
		log(this.getClass().getSimpleName() + " v0.4");
	}

	private void displayHelp(String[] args) {
		if (args.length > 1) {
			if (args[1].equals("log9000")) {
				log9000Help();
			} else {
				log("??? unknown command");
			}
			return;
		}
		log("/atr                               reset card");
		log("/send <apdu>                       send APDU");
		log("/select <aid>                      select the applet");
		log("/term                              open terminal");
		log("quit");
		//System.out.println("/checkapdu <apdu template> <response template>  e.g. ??000000 6D00 or 00??0000 6E00");
		// log("file <filename> [<responsefile>]  execute commands from a file");
		// log("ctrl");
		// log("desfire");
		// log("dump_ndef");
		// log("dump_mifare");
		//System.out.println("/jcop_id                           display information jcop product infos");
		// log("mifare");
		// log("log9000                           log9000 applet commands");
		
		
		//System.out.println("--gpj commands------------------------------------------");
		//System.out.println("ls                                list global registry");
		//System.out.println("upload <capfile>                  loads the given capfile");
		//log("install <pkgAID> <appAID>         install applet");
		//System.out.println("delete <pkgAID>                   delete package/applet");
		//System.out.println("--------------------------------------------------------");
		// log("ffca000000 Get UID Command\r\nffca010000 ");
		//System.out.println("--------------------------------------------------------");
		displayLibraryUsage();
	}

	private void displayLibraryUsage() {
		log("under linux maybe start with 'java -Dsun.security.smartcardio.library=/lib/x86_64-linux-gnu/libpcsclite.so.1 -jar MySmartCardIO.jar'");
		//log("TODO check for java 1.8");
	}

	/*
	 * JCOP identify command:
	 * http://sourceforge.net/p/globalplatform/mailman/globalplatform-users/thread/4E13B1D3.7050604@t-online.de/
	 * http://stackoverflow.com/questions/27077107/what-is-jcop-identify-applet-for
	 */
	private void identify() {
		try {
			byte[] id_command = new byte[] { (byte) 00, (byte) 0xA4, (byte) 04,
					00, (byte) 0x09, (byte) 0xA0, 00, 00, 01, (byte) 0x67,
					(byte) 0x41, (byte) 0x30, (byte) 00, (byte) 0xFF };
			byte[] response = sendAndDisplayApdu(id_command);
			log("response: "
					+ Util.byteArrayToHex(response, LINE_PREFIX));
			if (response.length == 21) {
				log("FABKEY ID: 0x"
						+ Util.toHexString(response, 0, 1));
				log("PATCH ID: 0x"
						+ Util.toHexString(response, 1, 1));
				log("MASK ID: 0x"
						+ Util.toHexString(response, 3, 1));
				log("MASK NAME: "
						+ Util.getAsciiDump(response, 8, 6));
				log("ROM INFO: 0x"
						+ Util.toHexString(response, 16, 3).replace(" ", ""));
			}
		} catch (Exception ex) {
			log(ex.toString());
		}
	}

	public void readDesfire() {
		/*
		 * Mifare Desfire communication example
		 * http://ridrix.wordpress.com/2009/
		 * 09/19/mifare-desfire-communication-example/
		 * 
		 * mifare DESFire, Contactless Multi-Application IC with DES and 3DES
		 * Security, MF3 IC D40
		 * http://read.pudn.com/downloads165/ebook/753406/M075031_desfire.pdf
		 * Mifare DESFIRE EV1 http://www.acs.com.hk/drivers/eng/TDS_DESF_EV1.pdf
		 * 
		 * native commands are wrapped inside iso7816 style APDUs cls ins p1 p2
		 * lc [data] le 90 [native ins] 00 00 lc [data] 00
		 * 
		 * 00 : Command successful af : More data (send command 'af' to fetch
		 * remaining data) 9d : Permission Denied
		 */
		try {

			// CardTerminal terminal = null;

			// show the list of available terminals
			// TerminalFactory factory = TerminalFactory.getDefault();
			// List<CardTerminal> terminals = factory.terminals().list();
			// log("Terminals: " + terminals);

			/*
			 * terminal = terminals.get(1);
			 * 
			 * 
			 * // Establish a connection with the card
			 * log("Waiting for a card..");
			 * terminal.waitForCardPresent(0);
			 * 
			 * establishConnection();
			 */
			// CM5321 + DESFire bondout => Get Application IDs: 6E00 Wrong
			// Instruction Class
			log("Get Application IDs: "
					+ Util.byteArrayToHex(sendAPDU(Util
							.fromHexToByte("906a000000")))); // 00: card blank,
																// REINERSCT:
																// 0083800183809100
			log("Get Version: "
					+ Util.byteArrayToHex(sendAPDU(Util
							.fromHexToByte("9060000000")))); // 0401010100160591AF
			log("Get Version: "
					+ Util.byteArrayToHex(sendAPDU(Util
							.fromHexToByte("90af000000")))); // 0401010103160591AF
			log("Get Version: "
					+ Util.byteArrayToHex(sendAPDU(Util
							.fromHexToByte("90af000000")))); // 047D863AFE2080BA14975D5027109100

			// SDI0 Desfire Command 0xFC 0xDE 0x00 0x00 Length Command

			/*
			 * Select PICC Application: --> 5a 00 00 00 <-- 00
			 */
			log("Select Application: "
					+ sendAPDU(Util.fromHexToByte("905a000000")));

			/*
			 * Get File IDs (for PICC Application): --> 6f <-- 9d Permission
			 * denied.
			 */

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public void dumpMifareWithSCL011(boolean defaultKey) {

		try {
			String key0 = "A0 A1 A2 A3 A4 A5";
			String key1 = "D3 F7 D3 F7 D3 F7";
			if (defaultKey) {
				key0 = "FF FF FF FF FF FF";
				key1 = key0;
			}
			String response = "";
			/*
			 * CardTerminal terminal = null;
			 * 
			 * // show the list of available terminals TerminalFactory factory =
			 * TerminalFactory.getDefault(); List<CardTerminal> terminals =
			 * factory.terminals().list(); log("Terminals: " +
			 * terminals);
			 * 
			 * terminal = terminals.get(0);
			 * 
			 * // Establish a connection with the card
			 * log("Waiting for a card..");
			 * terminal.waitForCardPresent(0);
			 * 
			 * establishConnection();
			 */
			// log("histbytes: " + send(new byte[] { },
			// channel));
			// STORAGE_CARD_CMDS_LOAD_KEYS
			send(Util.fromHexToByte("FF 82 00 60 06 " + key0), channel);
			// STORAGE_CARD_CMDS_AUTHENTICATE
			send(Util.fromHexToByte("FF 86 00 00 05 01 00 02 60 00"), channel);
			for (int b = 0; b < 4; b++) {
				// STORAGE_CARD_CMDS_READ_BINARY
				response = send(Util.fromHexToByte(String.format(
						"FF B0 00 %02x 10", b)), channel);
				if (response.endsWith("9000"))
					log(String.format("(%2d): ", b)
							+ response.substring(0, response.length() - 4));
				else
					throw new Exception("unexpected response" + response);
			}
			// STORAGE_CARD_CMDS_LOAD_KEYS
			response = send(Util.fromHexToByte("FF 82 00 60 06 " + key1),
					channel);
			if (!response.endsWith("9000"))
				throw new Exception("unexpected response" + response);
			// STORAGE_CARD_CMDS_AUTHENTICATE
			response = send(
					Util.fromHexToByte("FF 86 00 00 05 01 00 05 60 00"),
					channel);
			if (!response.endsWith("9000"))
				throw new Exception("unexpected response" + response);
			for (int b = 4; b <= 63; b++) {
				// STORAGE_CARD_CMDS_READ_BINARY
				response = send(Util.fromHexToByte(String.format(
						"FF B0 00 %02x 10", b)), channel);
				if (response.endsWith("9000"))
					log(String.format("(%2d): ", b)
							+ response.substring(0, response.length() - 4));
				else
					throw new Exception("unexpected response" + response);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public String send(byte[] cmd, CardChannel channel) {

		String res = "";

		byte[] baResp = new byte[258];
		ByteBuffer bufCmd = ByteBuffer.wrap(cmd);
		ByteBuffer bufResp = ByteBuffer.wrap(baResp);

		// output = The length of the received response APDU
		int output = 0;

		try {

			output = channel.transmit(bufCmd, bufResp);

		} catch (CardException ex) {
			ex.printStackTrace();
		}

		for (int i = 0; i < output; i++) {
			res += String.format("%02X", baResp[i]);
			// The result is formatted as a hexadecimal integer
		}
		return res;
	}

	public static boolean check9000(ResponseAPDU ra) {
		byte[] response = ra.getBytes();
		return (response[response.length - 2] == (byte) 0x90 && response[response.length - 1] == (byte) 0x00);
	}

	private String displayStatusword(byte[] response) {
		String sw = String.format("%02X%02X",
				response[response.length - 2] & 0xFF,
				response[response.length - 1] & 0xFF);
		String s = propSWs.getProperty(sw);
		if (s == null)
			return "";
		return "\r\n" + "Status: " + s;
	}

	private void readSWResource() {

		try {
			// load a properties file from class path, inside static method
			// prop.load(MySmartCardIO.class.getClassLoader().getResourceAsStream("config.properties");));
			propSWs.load(getClass().getClassLoader().getResourceAsStream(
					"SW.txt"));

			// get the property value and print it out
			// log(propErrors.getProperty("6A82"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}
}// MySmartCardIO
