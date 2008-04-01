//
// $Id$

package com.threerings.msoy.person.data;

import com.threerings.msoy.web.data.ServiceCodes;

/**
 * Codes and constants relating to the mail services.
 */
public interface ProfileCodes extends ServiceCodes
{
    /** An error code returned by the profile services. */
    public static final String E_BAD_USERNAME_PASS = "e.bad_username_pass";

    /** An error code returned by the profile services. */
    public static final String E_UNSUPPORTED_WEBMAIL = "e.unsupported_webmail";

    /** An error code returned by the profile service. */
    public static final String E_USER_INPUT_REQUIRED = "e.user_input_required";

    /** An error core returned by the profile service. */
    public static final String E_MAX_WEBMAIL_ATTEMPTS = "e.max_webmail_attempts";
}
