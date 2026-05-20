package com.decor.gateway.factories;

public class RequestTenantFactory {
    public static String generatePathType(String path){
        String result;
        if (path.startsWith("/api/v1/login"))
            result = "login";
        else
            result = "global";

        return result;
    }
}
