import java.io.UnsupportedEncodingException;
import java.io.*;

/**
 * Hexadecimal and string helper class.
 *
 */
public class Util {
	
	public static String byteArrayToHex(byte[] b) {
		return byteArrayToHex(b, "");
	}
	/** 
     * Return the given byte array as hex string.
     * @param b
     */
	public static String byteArrayToHex(byte[] b, String prefix)
    {
    	return toHexString(b, 0, b.length, prefix);
    }
	public static String toHexString(byte[] buf, int off, int len)
    {
		return toHexString(buf, off, len, "");
    }
    /**
     * Return the given byte array as hex string.
     * @param buf
     * @param off
     * @param len
     */
	public static String toHexString(byte[] buf, int off, int len, String prefix)
    {
      if (buf  == null)
      	return "null";
	  StringBuffer str = new StringBuffer();
      String HEX = "0123456789abcdef";

      for (int i = 0; i < len; i++)
        {
          str.append(HEX.charAt(buf[i+off] >>> 4 & 0x0F));
          str.append(HEX.charAt(buf[i+off] & 0x0F));
          str.append(' ');
          if (i % 16 == 15)
        	  str.append(System.getProperty("line.separator").toString() + prefix);
        }
      return str.toString();
    }
	 /** very conservative method for printing a byte array. */
	 public static String printable(byte[] data, int offset, int length)
	 {
	     StringBuffer buf = new StringBuffer(length);
	     for (int i = offset; i < length; ++i)
	     {
	         char ch = (char)(data[i] & 0xFF);
	         if ((ch >= 0x20) && (ch <= 0x7E))  // Only US-ASCII
	         {
	             buf.append(ch);
	         }
	         else
	         {
	             buf.append('?');
	         }
	     }
	     int len = length;
	     if (len > 80) len=80;
	     return buf.toString().concat(Util.toHexString(data, 0, len, "").concat(" l= ") + length);
	 }
    
	 /** Shift.*/
	public static String[] shift(String[] args)
	 {
	     String[] newArgs;
	     if (args.length == 0)
	     {
	         newArgs = args;
	     }
	     else
	     {
	         newArgs = new String[args.length - 1];
	         System.arraycopy(args, 1, newArgs, 0, args.length - 1);
	     }
	     return newArgs;
	 }
	public static String getByteDump(byte[] bytes, int offset, int length)
	 {
	 	if (bytes == null)
	 		return "null";
		int width = 16;
	 	String dump = "";
	 	try{
	 		for (int index = offset; index < length; index += width) {
	 			if (index+width > length)
	 				width=length-index;
	 			dump += "  " + getHexDump(bytes, index, width);
	 			dump += getAsciiDump(bytes, index, width)+ "\r\n";
	 		}
	 	}
	 	catch(Exception e)
		{
	 		dump += e.toString();
		}
	 	return dump;
	 }

	public static String getByteDump(byte[] bytes)
	 {
	 	return getByteDump(bytes,0,bytes.length);
	 }

	public static String getAsciiDump(byte[] bytes, int index, int width)
		throws UnsupportedEncodingException {
		String ret = "";
		String rep = "";
		if (index < bytes.length) {
			width = (index + width) > bytes.length ? bytes.length - index : width;
			/*rep = new String(bytes, index, width, "US-ASCII");
			//replaceAll is not implemented in the MIDP String class
			while (rep.indexOf('\r') > 0)
				rep = rep.replace('\r', '.');
			while (rep.indexOf('\n') > 0)
				rep = rep.replace('\n', '.');
			*/
			for (int j=0 ;  j < width;  j++)
            {
                int	ch;

                ch = bytes[index+j] & 0xFF;

                if (ch < 0x20  ||
                    ch >= 0x7F  &&  ch < 0xA0  ||
                    ch > 0xFF)
                {
                    // The character is unprintable
                    ch = '.';
                }

                rep +=((char) ch);
            }
			ret += rep;
		} else {
			ret += "";
		}
		return ret;
	}

	/* Return hex string buffer as 
	public static String fromHex(String convert) {
	 	   byte[] hexarr = null;
	 	   hexarr = convert.getBytes();
	 	   StringBuffer sb = new StringBuffer();
	 	   for (int ii=0; ii<hexarr.length; ii+=2) {
	 	      sb.append( Integer.parseInt(new String(hexarr, ii, 2), 16));
	 	   } return (sb.toString());
	 }
	 */
	/** Return hex string as byte buffer .*/
    public static byte[] fromHexToByte(String convert)
    {
    	byte[] hexarr = hexarr = convert.getBytes();
    	int pos=0;
    	String s, hx = "";
	 	ByteArrayOutputStream stream = new ByteArrayOutputStream(); 
	 	while (pos < hexarr.length) 
	 	{
	 		s = new String(hexarr, pos, 1);
	 		if ((s.charAt(0) >= '0' && s.charAt(0) <= '9' ) ||
	 		    (s.charAt(0) >= 'a' && s.charAt(0) <= 'f') ||
	 		    (s.charAt(0) >= 'A' && s.charAt(0) <= 'F'))
	 		{
	 			hx += s;
	 		}
	 		pos++;
	 		if (hx.length() == 2)
	 		{
	 			stream.write(Integer.parseInt(hx, 16));
	 			hx = "";
	 		}
	 	} 
	 	return stream.toByteArray();
    }
	public static String getHexDump(byte[] bytes, int offset, int width) {
	 	String ret="";
		for (int index = 0; index < width; index++) {
			if (index + offset < bytes.length) {
				String hexString = Integer.toHexString(0xFF & bytes[index + offset]);
				ret +=((hexString.length() < 2 ? "0" : "") + hexString + " ");
			} else {
				ret +=("   ");
			}
		}
		return ret;
	}
}
