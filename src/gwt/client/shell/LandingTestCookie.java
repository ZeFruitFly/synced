package client.shell;

import com.threerings.gwt.util.CookieUtil;
import com.threerings.msoy.web.gwt.ABTestUtil;
import com.threerings.msoy.web.gwt.CookieNames;

/**
 * Utility methods related to parsing landing tests given to us by the server in a cookie when we
 * first visit Whirled.
 */
public class LandingTestCookie
{
    /**
     * Gets the group assigned to the given visitor id for the given test name. Note that this
     * should only be called by new users, i.e. when the landing page is being accessed and there
     * is no previous member cookie.
     * @return the group (>=1) or -1 if the test is not active
     */
    public static int getGroup (String testName, String visitorId)
    {
        int numGroups = ABTestUtil.getNumGroups(get(), testName);
        return numGroups == 0 ? -1 : ABTestUtil.getGroup(visitorId, testName, numGroups);
    }

    /**
     * Gets the value of the landing test cookie, or the empty string if it is not set.
     */
    public static String get ()
    {
        String value = CookieUtil.get(CookieNames.LANDING_TEST);
        return value == null ? "" : value;
    }
}