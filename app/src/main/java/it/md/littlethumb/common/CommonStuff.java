package it.md.littlethumb.common;

public class CommonStuff {

	//path del server
	public static final String SERVER_PATH = "http://192.168.0.2/advbeacon";

	//autenticazione del server
	public static final String SERVER_USER = "..";
	public static final String SERVER_PASSWORD = "..";

    //timeout per la connessione con il server (millisecondi)
	public static int SERVER_CONNECT_TIMEOUT = 60000;
 	public static int SERVER_READ_TIMEOUT = 60000;
 	
	//crash report
	public static final String CRASH_REPORT_URL = SERVER_PATH + "/acra/errorReport.php";
	public static final String CRASH_REPORT_LOGIN ="";
	public static final String CRASH_REPORT_PASSWORD ="";
}
