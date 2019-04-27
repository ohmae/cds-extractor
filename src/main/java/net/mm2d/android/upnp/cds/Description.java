package net.mm2d.android.upnp.cds;

/**
 * @author <a href="mailto:ryo@mm2d.net">大前良介(OHMAE Ryosuke)</a>
 */
public class Description {
    private final int mStart;
    private final int mNumber;
    private final String mXml;

    Description(
            int start,
            int number,
            String xml) {
        mStart = start;
        mNumber = number;
        mXml = xml;
    }

    public int getStart() {
        return mStart;
    }

    public int getNumber() {
        return mNumber;
    }

    public String getXml() {
        return mXml;
    }
}
