package com.artclave.sandvikdatamule.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.artclave.sandvikdatamule.storage.FileStorage;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.io.IOException;

/**
 * Builder class for a folder picker dialog.*/


public class FolderPickerDialogBuilder extends AlertDialog.Builder {

    private ArrayAdapter<String> mAdapter;
    private AlertDialog mAlert;

    private File mRoot;

    public FolderPickerDialogBuilder(Context context, File root) {
        super(context);
        mRoot = root;

        mAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);

        update();

        ListView list = new ListView(getContext());
        list.setAdapter(mAdapter);
        list.setOnItemClickListener(
                (parentAdapterView, view, position, id) -> {
                    String dir = (String) parentAdapterView.getItemAtPosition(position);
                    final File parent;
                    if (dir.equals("..") && (parent = mRoot.getParentFile()) != null) {
                        mRoot = parent;
                    } else {
                        mRoot = new File(mRoot, dir);
                    }
                    update();
                }
        );

        setView(list);
    }

    @Override
    public AlertDialog create() {
        if (mAlert != null) throw new RuntimeException("Cannot reuse builder");
        mAlert = super.create();
        return mAlert;
    }

    private void update() {
        try {
            mRoot = new File(mRoot.getCanonicalPath());
        } catch (IOException e) {
            Cat.w("Directory root is incorrect, fixing to external storage.");
            mRoot = FileStorage.instance().getStorageRootPath();
        }

        if (mAlert != null) {
            mAlert.setTitle(mRoot.getAbsolutePath());
        } else {
            setTitle(mRoot.getAbsolutePath());
        }

        mAdapter.clear();
        String[] dirs = mRoot.list(
                (dir, filename) -> {
                    File file = new File(dir, filename);
                    return (file.isDirectory() && !file.isHidden());
                });
        if (dirs == null) {
            Cat.w("Unable to receive dirs list, no Access rights?");
            Cat.d("Unable to fix, continue with empty list");
            dirs = new String[]{};
        }
        mAdapter.add("..");
        mAdapter.addAll(dirs);
    }

    public AlertDialog.Builder setSelectedButton(int textId, OnSelectedListener listener) {
        return setPositiveButton(textId,
                (dialog, which) -> listener.onSelected(mRoot.getAbsolutePath()));
    }

    public interface OnSelectedListener {
        void onSelected(String path);
    }

}

