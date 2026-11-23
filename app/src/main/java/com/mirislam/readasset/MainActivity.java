package com.mirislam.readasset;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PDF_CACHE_SUBDIR = "pdfs";

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Apply system bar insets for Android 15+ (API 35+) edge-to-edge compatibility.
        View rootView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        TableLayout table = findViewById(R.id.tableForPDF);
        List<String> fileNames = getPDFFromAssets();
        Log.d(TAG, "Number of PDF files in assets folder: " + fileNames.size());

        int padHorizontal = getResources().getDimensionPixelSize(R.dimen.row_padding_horizontal);
        int padVertical = getResources().getDimensionPixelSize(R.dimen.row_padding_vertical);
        float textSizePx = getResources().getDimension(R.dimen.textsizeList);

        int rowCount = 0;
        for (final String fileName : fileNames) {
            TableRow row = new TableRow(this);
            row.setBackgroundColor(rowCount % 2 == 0 ? Color.parseColor("#F0F0F0") : Color.WHITE);
            row.setPadding(padHorizontal, padVertical, padHorizontal, padVertical);
            row.setClickable(true);
            row.setFocusable(true);
            rowCount++;

            TextView textView = new TextView(this);
            textView.setHapticFeedbackEnabled(true);
            textView.setText(stripPdfExtension(fileName));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
            textView.setTextColor(Color.BLACK);

            row.addView(textView);
            row.setOnClickListener(v -> openPdfAsset(fileName));

            table.addView(row, new TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }

    /**
     * Copies the requested PDF from assets into the app's private cache directory on a background
     * thread, then launches an external PDF viewer using a FileProvider content:// URI.
     *
     * <p>No external storage permissions are required because the cached file lives inside
     * {@link #getCacheDir()} and read access is granted temporarily via
     * {@link Intent#FLAG_GRANT_READ_URI_PERMISSION}.
     */
    private void openPdfAsset(final String fileName) {
        ioExecutor.execute(() -> {
            File cachedFile;
            try {
                cachedFile = copyFileFromAssets(fileName);
            } catch (IOException e) {
                Log.e(TAG, "Error copying file from assets: " + fileName, e);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(MainActivity.this,
                                R.string.error_copy_pdf,
                                Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    launchPdfViewer(cachedFile);
                }
            });
        });
    }

    private void launchPdfViewer(File file) {
        String authority = getPackageName() + ".fileprovider";
        Uri uri = FileProvider.getUriForFile(this, authority, file);
        Log.i(TAG, "Launching PDF viewer for " + file.getName() + " (" + uri + ")");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("", uri));

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No PDF viewer installed", e);
            Toast.makeText(this, R.string.error_no_pdf_viewer, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Copies a file from the APK's {@code assets/} folder into {@code getCacheDir()/pdfs/}.
     *
     * @param fileName name of the PDF asset
     * @return the cached {@link File} ready to be shared via {@link FileProvider}
     */
    private File copyFileFromAssets(String fileName) throws IOException {
        File cacheDir = new File(getCacheDir(), PDF_CACHE_SUBDIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create cache directory: " + cacheDir.getAbsolutePath());
        }

        File cacheFile = new File(cacheDir, fileName);
        if (cacheFile.exists() && cacheFile.length() > 0) {
            Log.d(TAG, "Using cached PDF at: " + cacheFile.getAbsolutePath());
            return cacheFile;
        }

        Log.d(TAG, "Copying asset to cache: " + cacheFile.getAbsolutePath());
        File tempFile = new File(cacheDir, fileName + ".tmp");
        try (InputStream in = getAssets().open(fileName);
             OutputStream out = new FileOutputStream(tempFile)) {
            copyStream(in, out);
            out.flush();
        } catch (IOException e) {
            // Clean up any partial write before propagating the error.
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            throw e;
        }

        if (!tempFile.renameTo(cacheFile)) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            throw new IOException("Failed to finalize cached file: " + cacheFile.getAbsolutePath());
        }
        return cacheFile;
    }

    private void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /**
     * Returns a sorted list of all PDF files in the root of the {@code assets/} directory.
     */
    private List<String> getPDFFromAssets() {
        List<String> pdfFiles = new ArrayList<>();
        AssetManager assetManager = getAssets();

        try {
            String[] assets = assetManager.list("");
            if (assets != null) {
                for (String name : assets) {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        pdfFiles.add(name);
                    }
                }
            }
            Collections.sort(pdfFiles);
        } catch (IOException e) {
            Log.e(TAG, "Could not read files from assets folder", e);
            Toast.makeText(this, R.string.error_read_assets, Toast.LENGTH_LONG).show();
        }

        return pdfFiles;
    }

    static String stripPdfExtension(String fileName) {
        return fileName.replaceFirst("(?i)\\.pdf$", "");
    }
}
