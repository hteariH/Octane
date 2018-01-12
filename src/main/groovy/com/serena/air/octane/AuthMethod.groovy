package com.serena.air.octane

enum AuthMethod {

    API_KEY,
    USER_PASS;

    public static AuthMethod getMethod(String method) {
        switch (method) {
            case 'API_KEY': return API_KEY
            case 'USER_PASS': return USER_PASS
        }
    }
}