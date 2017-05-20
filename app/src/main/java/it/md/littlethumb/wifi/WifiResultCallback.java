/*
 * Created on Feb 22, 2012
 * Author: Paul Woelfel
 * Email: frig@frig.at
 */
package it.md.littlethumb.wifi;

import it.md.littlethumb.model.WifiScanResult;

public interface WifiResultCallback {
	public void onScanFinished(WifiScanResult wr);
	public void onScanFailed(Exception ex);
}
