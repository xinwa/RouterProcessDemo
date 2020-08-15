package com.xiwna.processor.router;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;

import java.util.Set;

/**
 * @author xingping
 */
public class Mapping {
    private final String path;
    private final Class<? extends Activity> activity;
    private final MethodInvoker method;
    private String formatHost;

    public Mapping(String path, Class<? extends  Activity> activity, MethodInvoker method) {
        this.path = path;
        this.activity = activity;
        this.method = method;
        formatHost = Uri.parse(path).getHost();
    }

    public MethodInvoker getMethod() {
        return method;
    }

    public Class<? extends Activity> getActivity() {
        return activity;
    }

    public String getPath() {
        return path;
    }

    public boolean match(Uri uri) {
        return this.formatHost.equals(uri.getHost());
    }

    public Bundle parseExtras(Uri uri) {
        Bundle bundle = new Bundle();
        Set<String> names = UriCompact.getQueryParameterName(uri);
        for (String name: names) {
            String value = uri.getQueryParameter(name);
            put(bundle, name, value);
        }
        return bundle;
    }

    private void put(Bundle bundle, String name, String value) {
        bundle.putString(name, value);
    }
}
