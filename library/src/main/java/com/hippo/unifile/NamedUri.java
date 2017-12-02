package com.hippo.unifile;

import android.net.Uri;

class NamedUri {
    final Uri uri;
    final String name;

    NamedUri(Uri uri, String name) {
        this.uri = uri;
        this.name = name;
    }
}