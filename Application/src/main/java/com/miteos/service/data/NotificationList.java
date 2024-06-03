package com.miteos.service.data;

import java.io.Serializable;
import java.util.ArrayList;

public class NotificationList implements Serializable {
    public int count = 0;
    public ArrayList<NotificationBundle> nBundleList = new ArrayList<>();
}
