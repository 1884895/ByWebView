package me.jingbin.web;

import android.net.Uri;
import android.webkit.ValueCallback;

public interface OnChromeClientCallback {
    boolean openFileChooserPermission(ValueCallback<Uri[]> uploadMsg);
}
