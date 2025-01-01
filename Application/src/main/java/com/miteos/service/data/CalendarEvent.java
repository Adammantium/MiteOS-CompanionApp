package com.miteos.service.data;

import java.io.Serializable;

public class CalendarEvent implements Serializable {
    public long id;
    public String title;
    public String description;
    public long startTime;
    public long endTime;
    public String location;
    public boolean allDay;
    public String calendarId;
    public String calendarName;
} 