package com.kleinkauf.users.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/*
Dates are stored as LocalDateTime in server time (in Docker, UTC). Without the offset, the browser
reads them as local time and shows them shifted: platform responses send them with their offset.
 */
public final class Dates {

    private Dates() {
    }

    public static OffsetDateTime withServerOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
