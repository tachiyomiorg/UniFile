/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.unifile;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class TreeDocumentFile extends UniFile {

    private static final String TAG = TreeDocumentFile.class.getSimpleName();

    private final Context mContext;
    private Uri mUri;
    private String mName;

    TreeDocumentFile(UniFile parent, Context context, Uri uri) {
        this(parent, context, uri, null);
    }

    TreeDocumentFile(UniFile parent, Context context, Uri uri, String name) {
        super(parent);
        mContext = context.getApplicationContext();
        mUri = uri;
        mName = name;
    }

    @Override
    public UniFile createFile(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return null;
        }

        UniFile child = findFile(displayName);

        if (child != null) {
            if (child.isFile()) {
                return child;
            } else {
                Log.w(TAG, "Try to create file " + displayName + ", but it is not file");
                return null;
            }
        } else {
            // FIXME There's nothing about display name and extension mentioned in document.
            // But it works for com.android.externalstorage.documents.
            // The safest way is use application/octet-stream all the time,
            // But media store will not be updated.
            int index = displayName.lastIndexOf('.');
            if (index > 0) {
                String name = displayName.substring(0, index);
                String extension = displayName.substring(index + 1);
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (!TextUtils.isEmpty(mimeType)) {
                    final Uri result = DocumentsContractApi21.createFile(mContext, mUri, mimeType, name);
                    return (result != null) ? new TreeDocumentFile(this, mContext, result) : null;
                }
            }

            // Not dot in displayName or dot is the first char or can't get MimeType
            final Uri result = DocumentsContractApi21.createFile(mContext, mUri, "application/octet-stream", displayName);
            return (result != null) ? new TreeDocumentFile(this, mContext, result) : null;
        }
    }

    @Override
    public UniFile createDirectory(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return null;
        }

        UniFile child = findFile(displayName);

        if (child != null) {
            if (child.isDirectory()) {
                return child;
            } else {
                return null;
            }
        } else {
            final Uri result = DocumentsContractApi21.createDirectory(mContext, mUri, displayName);
            return (result != null) ? new TreeDocumentFile(this, mContext, result) : null;
        }
    }

    @NonNull
    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    public String getName() {
        if (mName == null) {
            mName = DocumentsContractApi19.getName(mContext, mUri);
        }
        return mName;
    }

    @Override
    public String getType() {
        final String type = DocumentsContractApi19.getType(mContext, mUri);
        if (!TextUtils.isEmpty(type)) {
            return type;
        } else {
            return Utils.getTypeForName(getName());
        }
    }

    @Nullable
    @Override
    public String getFilePath() {
        return DocumentsContractApi19.getFilePath(mContext, mUri);
    }

    @Override
    public boolean isDirectory() {
        return DocumentsContractApi19.isDirectory(mContext, mUri);
    }

    @Override
    public boolean isFile() {
        return DocumentsContractApi19.isFile(mContext, mUri);
    }

    @Override
    public long lastModified() {
        return DocumentsContractApi19.lastModified(mContext, mUri);
    }

    @Override
    public long length() {
        if (isDirectory()) {
            return -1L;
        } else {
            return DocumentsContractApi19.length(mContext, mUri);
        }
    }

    @Override
    public boolean canRead() {
        return DocumentsContractApi19.canRead(mContext, mUri);
    }

    @Override
    public boolean canWrite() {
        return DocumentsContractApi19.canWrite(mContext, mUri);
    }

    @Override
    public boolean delete() {
        invalidateName();
        return DocumentsContractApi19.delete(mContext, mUri);
    }

    @Override
    public boolean exists() {
        return DocumentsContractApi19.exists(mContext, mUri);
    }

    @Override
    public UniFile[] listFiles() {
        if (!isDirectory()) {
            return null;
        }

        final NamedUri[] result = DocumentsContractApi21.listFilesNamed(mContext, mUri);
        final UniFile[] resultFiles = new UniFile[result.length];
        for (int i = 0, n = result.length; i < n; i++) {
            NamedUri namedUri = result[i];
            resultFiles[i] = new TreeDocumentFile(this, mContext, namedUri.uri, namedUri.name);
        }
        return resultFiles;
    }

    @Override
    public UniFile[] listFiles(FilenameFilter filter) {
        if (filter == null) {
            return listFiles();
        }

        if (!isDirectory()) {
            return null;
        }

        final NamedUri[] uris = DocumentsContractApi21.listFilesNamed(mContext, mUri);
        final ArrayList<UniFile> results = new ArrayList<>();
        for (NamedUri uri : uris) {
            if (uri.name != null && filter.accept(this, uri.name)) {
                results.add(new TreeDocumentFile(this, mContext, uri.uri, uri.name));
            }
        }
        return results.toArray(new UniFile[results.size()]);
    }

    @Override
    public UniFile findFile(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return null;
        }

        if (!isDirectory()) {
            return null;
        }

        // This implementation assumes that document IDs are formed
        // based on filenames, which is a reasonable assumption for
        // most document providers, but is not guaranteed by the spec.
        //
        // Without making the assumption that document IDs are
        // arranged in a reasonable way, it is impossible to check for
        // file existence in a way that is not extremely slow.
        //
        // If it turns out that some popular devices use a document
        // provider for which this is a bad assumption, then we should
        // revisit this implementation and perhaps special-case a
        // fallback to the slow way for those providers. It's possible
        // to check the name of the document provider by using similar
        // code to DocumentsContractApi19.isDocumentsProvider to
        // identify which provider is being used, and then print the
        // name of the class.
        //
        // Note on case sensitivity: this method should always behave
        // correctly with respect to the case sensitivity of the
        // filesystem. That is, on a case-sensitive filesystem it will
        // do a case-sensitive existence check, while on a
        // case-insensitive filesystem (case-preserving or not) it
        // will implicitly do a case-insensitive existence check.
        // Previous versions of this method took an explicit parameter
        // for whether the check should be case sensitive or not, but
        // that should no longer be necessary with the current
        // implementation.

        String documentId = DocumentsContract.getDocumentId(mUri);
        documentId += "/" + displayName;

        Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(mUri, documentId);
        UniFile child = new TreeDocumentFile(this, mContext, documentUri, displayName);

        if (child.exists()) {
            return child;
        } else {
            return null;
        }
    }

    @Override
    public boolean renameTo(String displayName) {
        invalidateName();
        final Uri result = DocumentsContractApi21.renameTo(mContext, mUri, displayName);
        if (result != null) {
            mUri = result;
            return true;
        } else {
            return false;
        }
    }

    @NonNull
    @Override
    public OutputStream openOutputStream() throws IOException {
        if (isDirectory()) {
            throw new IOException("Can't open OutputStream from a directory");
        }

        OutputStream os;
        try {
            os = mContext.getContentResolver().openOutputStream(mUri);
        } catch (Exception e) {
            throw new IOException("Can't open OutputStream");
        }
        if (os == null) {
            throw new IOException("Can't open OutputStream");
        }
        return os;
    }

    @NonNull
    @Override
    public OutputStream openOutputStream(boolean append) throws IOException {
        if (isDirectory()) {
            throw new IOException("Can't open OutputStream from a directory");
        }

        OutputStream os;
        try {
            os = mContext.getContentResolver().openOutputStream(mUri, append ? "wa" : "w");
        } catch (Exception e) {
            throw new IOException("Can't open OutputStream");
        }
        if (os == null) {
            throw new IOException("Can't open OutputStream");
        }
        return os;
    }

    @NonNull
    @Override
    public InputStream openInputStream() throws IOException {
        if (isDirectory()) {
            throw new IOException("Can't open InputStream from a directory");
        }

        InputStream is;
        try {
            is = mContext.getContentResolver().openInputStream(mUri);
        } catch (Exception e) {
            throw new IOException("Can't open InputStream");
        }
        if (is == null) {
            throw new IOException("Can't open InputStream");
        }
        return is;
    }

    @NonNull
    @Override
    public UniRandomAccessFile createRandomAccessFile(String mode) throws IOException {
        // Check file
        if (!isFile()) {
            throw new IOException("Can't make sure it is file");
        }

        ParcelFileDescriptor pfd;
        try {
            pfd = mContext.getContentResolver().openFileDescriptor(mUri, mode);
        } catch (Exception e) {
            throw new IOException("Can't open ParcelFileDescriptor");
        }
        if (pfd == null) {
            throw new IOException("Can't open ParcelFileDescriptor");
        }

        return new RawRandomAccessFile(TrickRandomAccessFile.create(pfd, mode));
    }

    private void invalidateName() {
        mName = null;
    }
}
