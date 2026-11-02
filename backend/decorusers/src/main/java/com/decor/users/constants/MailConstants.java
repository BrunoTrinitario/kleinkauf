package com.decor.users.constants;

public class MailConstants {
    public static String FORGOT_SUBJECT = "Reset password";
    public static String FORGOT_MAIL_CONTENT(String link){
        return "Use this link to create the a new password: "+link;
    }
}
