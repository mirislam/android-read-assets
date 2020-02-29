package com.mirislam.readasset;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    private String TAG =" MainAcitivity";
    // Folder where we would copy the pdf file and store from assets folder
    // This is necessary to let a viewing app to pickup the file from a globally accessible directory
    private String tmpFolder = Environment.getExternalStorageDirectory().getPath() + "/pdfFiles";
    // Permission code to write to external storage
    private static final int EXT_STORAGE_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if the permission is already there. If not, we need user to grant it
        // Manifest.permission.WRITE_EXTERNAL_STORAGE

        // We do not have permission. So need to request it
        if (ContextCompat.checkSelfPermission(
                MainActivity.this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(
                            MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            EXT_STORAGE_PERMISSION_CODE);
            Log.d(TAG, "After getting permission: "+ Manifest.permission.WRITE_EXTERNAL_STORAGE + " " + ContextCompat.checkSelfPermission(
                    MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE));

        } else {
            // We were granted permission already before
            Log.d(TAG, "Already has permission to write to external storage");
        }



        // Find the table layout by id and start populating the rows. The layout is defined in layout/conent_main.xml
        TableLayout table = (TableLayout) this.findViewById(R.id.tableForPDF);

        // get list of pdf files in assets folder
        List<String> fileNames = getPDFFromAssets();
        Log.d(TAG,"Number of pdf files in assets folder:" + fileNames.size());
        // just to alternate row color. Probably better way out there?
        int rowCount = 0;
        for (final String fileName : fileNames) {
            // create a new TableRow
            TableRow row = new TableRow(this);

            if (rowCount % 2 == 0) {
                row.setBackgroundColor(Color.LTGRAY);
            } else {
                row.setBackgroundColor(Color.WHITE);
            }
            rowCount++;

            // create a new TextView for showing file data
            TextView t = new TextView(this);
            // give user feedback that he has clicked
            t.setHapticFeedbackEnabled(true);
            // remove trailing .pdf
            String nfileName = fileName.replaceAll(".pdf", "");
            t.setText(nfileName);
            t.setTextSize(getResources().getDimension(R.dimen.textsizeList));

            // add the TextView to the new TableRow
            row.addView(t);
            row.setPadding(2, 5, 2, 5);
            // Add click listener to launch external pdf viewer (most likely Google drive)
            row.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {


                    // copy the file to external storage accessible by all
                    copyFileFromAssets(fileName);

                    /** PDF reader code */
                    Uri uri = null;
                    File file = new File(tmpFolder
                            + "/"
                            + fileName);

                    uri = FileProvider.getUriForFile(MainActivity.this,
                            getString(R.string.file_provider_authority),
                            file);
                    Log.i(TAG, "Launching viewer " + fileName + " " + file.getAbsolutePath());

                    //Intent intent = new Intent(Intent.ACTION_VIEW, FileProvider.getUriForFile(v.getContext(), "org.eicsanjose.quranbasic.fileprovider", file));

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    //intent.setDataAndType(Uri.fromFile(file), "application/pdf");
                    intent.setDataAndType(uri, "application/pdf");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        Log.i(TAG,"Staring pdf viewer activity");
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, e.getMessage());
                    }

                }

            });
            // add the TableRow to the TableLayout
            table.addView(row, new TableLayout.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT));

        }

    }


    /**
     * Helper method to copy a file from assets folder to a tmp folder which can be accessed by other
     * applications.
     * @param fileName name of the file to copy
     */
    private void copyFileFromAssets(String fileName) {


        Log.i(TAG, "Copy file from asset:" + fileName);

        AssetManager assetManager = this.getAssets();


        // file to copy to from assets
        File cacheFile = new File( tmpFolder + "/" + fileName );
        InputStream in = null;
        OutputStream out = null;
        try {
            Log.d(TAG,"Copying from assets folder to cache folder");
            if ( cacheFile.exists() ) {
                // already there. Do not copy
                Log.d(TAG, "Cache file exists at:" + cacheFile.getAbsolutePath());
                return;
            } else {
                Log.d(TAG, "Cache file does NOT exist at:" + cacheFile.getAbsolutePath());
                // TODO: There should be some error catching/validation etc before proceeding
                in = assetManager.open(fileName);
                out = new FileOutputStream(cacheFile);
                copyFile(in, out);

                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;

            }

        } catch (IOException ioe) {
            Log.e(TAG, "Error in copying file from assets " + fileName);
            ioe.printStackTrace();

        }

    }

    /**
     * Helper method to copy file from origin to target
     * @param in InputStream of the original file
     * @param out OutputStream of the destination file
     * @throws IOException
     */
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * This method will return all pdf files found in the assets folder. Note: will not traverse
     * nested directory. Returned list is not sorted
     * @return List of file names
     */
    private List<String> getPDFFromAssets() {
        List<String> pdfFiles = new ArrayList<>();
        AssetManager assetManager = this.getAssets();

        try {
            for (String name : assetManager.list("")) {
                // include files which end with pdf only
                if (name.toLowerCase().endsWith("pdf")) {
                    pdfFiles.add(name);
                }
            }
        } catch (IOException ioe) {
            String mesg = "Could not read files from assets folder";
            Log.e(TAG, mesg);
            Toast.makeText(MainActivity.this,
                    mesg,
                    Toast.LENGTH_LONG)
                    .show();


        }
        ;

        return pdfFiles;

    }

    // THis is invoked upon grant/denying the request for permission
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super
                .onRequestPermissionsResult(requestCode,
                        permissions,
                        grantResults);

        Log.d(TAG,"Request for write permission to external storage result:" + permissions[0] + " " + grantResults[0]);
        // Now let us make sure our cache dir exists. This would not work if user denied. But then again
        // in that case the whole app will not work. Add error checking
        File tmpDir = new File(tmpFolder);
        if (!tmpDir.exists()) {
            Log.d(TAG,"Tmp dir to store pdf does not exist");
            tmpDir.mkdir();
            Log.d(TAG,"Tmpdir created " + tmpDir.exists());
        } else {
            Log.d(TAG,"Tmpdir already exists " + tmpDir.exists());
        }

    }
}
