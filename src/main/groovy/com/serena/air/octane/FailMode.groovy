package com.serena.air.octane

enum FailMode {

    WARN_ONLY,
    FAIL_FAST,           // Fail if any is not in expected status
    FAIL_ON_NO_UPDATES,  // Fail if ALL are not in expected status
    FAIL_ON_ANY_FAILURE; // Fail if any is not in expected status (but only after all are checked)

    public static FailMode getMode(String mode) {
        switch (mode) {
            case 'WARN_ONLY': return WARN_ONLY
            case 'FAIL_FAST': return FAIL_FAST
            case 'FAIL_ON_NO_UPDATES': return FAIL_ON_NO_UPDATES
            case 'FAIL_ON_ANY_FAILURE': return FAIL_ON_ANY_FAILURE
        }
    }
}