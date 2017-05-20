package it.md.littlethumb.bluetooth;

/**
 * @author  Massimiliano D'Amico (massimo.damico@gmail.com)
 */
public class BeaconSession {

    private static BeaconSession instance;

    private BeaconSession() {
    }

    public static BeaconSession getInstance() {
        if (instance == null) {
            instance = new BeaconSession();
        }
        return instance;
    }

    //state constants
    public static final int SCANNING_OFF = 0;
    public static final int UPDATE_USER_POSITION = 1;
    public static final int GET_DETECTED_BEACONS = 2;

    //scanning max count
    public static final int MAX_SCANNING_COUNT = 5;

    //site
    private Integer siteId = null;

    public Integer getSiteId() {
        return siteId;
    }
    public void setSiteId(Integer siteId) {
        this.siteId = siteId;
    }
}
