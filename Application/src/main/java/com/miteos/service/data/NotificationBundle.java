package com.miteos.service.data;

import java.io.Serializable;

public class NotificationBundle implements Serializable {

    public int id;
    public String pName;       // Package name
    public String appName;     // Application name (label)
    public String category;
    public String title;
    public String text;
    public String subText;
}
