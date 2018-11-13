package cn.kaisay.azure.webapp.voteapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Base64;

public class Storagetest {

    private static String accountname = "kaixsa3" + "\n" ;
    private static String signedpermissions = "rwdlac"+ "\n" ;
    private static String signedservice = "b"+ "\n" ;
    private static String signedresourcetype = "sco"+ "\n" ;
    private static String signedstart = "2018-11-13T13:49:09Z"+ "\n" ;
    private static String signedexpiry = "2018-11-13T21:49:09Z"+ "\n" ;
    private static String signedIP = "168.1.5.65" +"\n" ;
    private static String signedProtocol = "https"+ "\n" ;
    private static String signedversion = "2017-11-09"+"\n" ;


    private static byte[]  keyBytes = Base64.getDecoder().decode("ns7kBUbZG8aCReOO/lFOD7jHrl9cN4MCsSLQ8Jb2DVnAdG9h3ERgafFIRWT5i43wOLK18mw2ubklEFeZp7sibA==");

    public static void main(String... args) throws UnsupportedEncodingException {

        String StringToSign = accountname+signedpermissions+signedservice+signedresourcetype+signedstart
                                +signedexpiry+signedIP+signedProtocol+signedversion;
        System.out.println(URLEncoder.encode(SignUp(keyBytes,StringToSign)));

    }


    public static String SignUp(byte[] secretKey, String plain) throws UnsupportedEncodingException {

        String plainEncode = URLDecoder.decode(plain,"UTF8");
        byte[] plainBytes = plainEncode.getBytes();

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(keyBytes, "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hashs = sha256_HMAC.doFinal(plainBytes);
/*          StringBuilder sb = new StringBuilder();
                for (byte x : hashs) {
                String b = Integer.toHexString(x & 0XFF);
                if (b.length() == 1) {
                    b = '0' + b;
                }
//	          sb.append(String.format("{0:x2}", x));
                sb.append(b);
            }
            */
            String hash = Base64.getEncoder().encodeToString(hashs);
	       return hash;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
