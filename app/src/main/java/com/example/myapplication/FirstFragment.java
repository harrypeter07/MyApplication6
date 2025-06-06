package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import android.util.Log;
import org.json.JSONArray;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.io.File;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.FileOutputStream;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FirstFragment extends Fragment implements FileAdapter.OnFileClickListener, FileAdapter.OnDownloadClickListener {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private FileAdapter fileAdapter;
    private Button permissionButton;
    private FrameLayout rootLayout;
    private static final int REQUEST_STORAGE_PERMISSION = 123;
    private static final int REQUEST_MANAGE_ALL_FILES = 124;
    private static final String BASE_URL = "https://friday1-3.onrender.com/";
    private OkHttpClient okHttpClient = new OkHttpClient();
    private FloatingActionButton refreshFab;
    private static final long TEN_MB = 10 * 1024 * 1024;
    private static final long TWENTY_MB = 20 * 1024 * 1024;
    private List<String> autoDownloaded = new ArrayList<>();
    private List<SectionFragment.FileItem> allFiles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_first, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        rootLayout = (FrameLayout) view;
        refreshFab = view.findViewById(R.id.fab_refresh);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        fileAdapter = new FileAdapter(new ArrayList<>(), this, this);
        recyclerView.setAdapter(fileAdapter);

        refreshFab.setOnClickListener(v -> fetchFiles());

        if (hasStoragePermission()) {
            showFileListUI();
            fetchFiles();
        } else {
            showPermissionButton();
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            fetchFiles();
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            return Environment.isExternalStorageManager();
        } else {
            // Android 10 and below
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showPermissionButton() {
        if (permissionButton == null) {
            permissionButton = new Button(getContext());
            permissionButton.setText("Allow Storage Permission");
            permissionButton.setOnClickListener(v -> requestStoragePermission());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.CENTER;
            rootLayout.addView(permissionButton, params);
        }
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        permissionButton.setVisibility(View.VISIBLE);
    }

    private void hidePermissionButton() {
        if (permissionButton != null) {
            rootLayout.removeView(permissionButton);
            permissionButton = null;
        }
    }

    private void showFileListUI() {
        recyclerView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        hidePermissionButton();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Request All Files Access
            requestAllFilesAccess();
        } else {
            // Android 10 and below - Request traditional storage permissions
            requestLegacyStoragePermissions();
        }
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                new AlertDialog.Builder(getContext())
                        .setTitle("Storage Permission Required")
                        .setMessage("This app needs access to manage all files on your device to function properly. Please grant 'All files access' permission in the next screen.")
                        .setPositiveButton("Continue", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                            startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            Toast.makeText(getContext(), "Storage permission is required for app functionality", Toast.LENGTH_LONG).show();
                        })
                        .show();
            } catch (Exception e) {
                // Fallback to general settings
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_MANAGE_ALL_FILES);
            }
        }
    }

    private void requestLegacyStoragePermissions() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs storage permission to read and write files on your device.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        requestPermissions(new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, REQUEST_STORAGE_PERMISSION);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Toast.makeText(getContext(), "Storage permission is required for app functionality", Toast.LENGTH_LONG).show();
                    })
                    .show();
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getContext(), "Storage permission granted!", Toast.LENGTH_SHORT).show();
                showFileListUI();
                fetchFiles();
            } else {
                Toast.makeText(getContext(), "Storage permission denied. Some features may not work.", Toast.LENGTH_LONG).show();
                showPermissionButton();
                new AlertDialog.Builder(getContext())
                        .setTitle("Permission Required")
                        .setMessage("Storage permission is essential for this app. Would you like to go to settings and enable it manually?")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_ALL_FILES) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(getContext(), "All files access granted!", Toast.LENGTH_SHORT).show();
                    showFileListUI();
                    fetchFiles();
                } else {
                    Toast.makeText(getContext(), "All files access denied. Some features may not work.", Toast.LENGTH_LONG).show();
                    showPermissionButton();
                    new AlertDialog.Builder(getContext())
                            .setTitle("Permission Required")
                            .setMessage("All files access is essential for this app. Would you like to go to settings and enable it manually?")
                            .setPositiveButton("Go to Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
            }
        }
    }

    private void fetchFiles() {
        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(getContext(), "Connecting to server...", Toast.LENGTH_SHORT).show();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://friday1-3.onrender.com/api/files")
                .build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(getContext(), "Could not connect to server: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Failed to load files from server", Toast.LENGTH_SHORT).show();
                        });
                    }
                    return;
                }
                String json = response.body().string();
                List<SectionFragment.FileItem> fileItems = new ArrayList<>();
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        fileItems.add(new SectionFragment.FileItem(obj.getString("filename"), obj.getString("uploaded_at")));
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Error parsing file list", Toast.LENGTH_SHORT).show();
                        });
                    }
                    return;
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        allFiles = fileItems;
                        fileAdapter.setFiles(fileItems);
                        recyclerView.setVisibility(View.VISIBLE);
                        hidePermissionButton();
                        if (fileItems.isEmpty()) {
                            Toast.makeText(getContext(), "No files found on server.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getContext(), "Connected to server. Files loaded!", Toast.LENGTH_SHORT).show();
                        }
                        // Auto-download files < 10MB
                        autoDownloaded.clear();
                        for (SectionFragment.FileItem file : fileItems) {
                            fetchFileSizeAndMaybeDownload(file.filename);
                        }
                    });
                }
            }
        });
    }

    private void fetchFileSizeAndMaybeDownload(String fileName) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://friday1-3.onrender.com/files/" + fileName;
        Request headRequest = new Request.Builder().url(url).head().build();
        client.newCall(headRequest).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) { /* ignore */ }
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                long size = 0;
                try {
                    String len = response.header("Content-Length");
                    if (len != null) size = Long.parseLong(len);
                } catch (Exception ignored) {}
                if (size > 0 && size < TWENTY_MB && !autoDownloaded.contains(fileName)) {
                    autoDownloaded.add(fileName);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> downloadFileSilently(fileName));
                    }
                }
            }
        });
    }

    private File getPublicDownloadsFile(String fileName) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        return new File(downloadsDir, fileName);
    }

    private void downloadFileSilently(String fileName) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://friday1-3.onrender.com/files/" + fileName;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) { /* ignore */ }
            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                if (!response.isSuccessful()) return;
                File outFile = getPublicDownloadsFile(fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                    fos.write(response.body().bytes());
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void onFileClick(String fileName) {
        if (isImageFile(fileName)) {
            showImageViewer(fileName);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "Downloading " + fileName + "...", Toast.LENGTH_SHORT).show();
            OkHttpClient client = new OkHttpClient();
            String url = "https://friday1-3.onrender.com/files/" + fileName;
            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(getContext(), "Failed to download file", Toast.LENGTH_SHORT).show();
                            });
                        }
                        return;
                    }
                    File outFile = getPublicDownloadsFile(fileName);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                        fos.write(response.body().bytes());
                    } catch (Exception e) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(getContext(), "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                        return;
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Downloaded to: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                            try {
                                Uri fileUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", outFile);
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(fileUri, getMimeType(fileName));
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(getContext(), "Cannot open file. Use a file manager.", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            });
        }
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private void showImageViewer(String fileName) {
        File imageFile = getPublicDownloadsFile(fileName);
        if (!imageFile.exists()) {
            Toast.makeText(getContext(), "Image not downloaded yet. Please tap again after a moment.", Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView imageView = new ImageView(getContext());
        imageView.setImageBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dialog.setContentView(imageView);
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private String getMimeType(String fileName) {
        String type = null;
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null) {
            type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type != null ? type : "application/octet-stream";
    }

    @Override
    public void onFileLongClick(String fileName, View anchor) {
        Toast.makeText(getContext(), "Long clicked: " + fileName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDownloadClick(String fileName) {
        downloadFileFromServer(fileName);
    }

    private void downloadFileFromServer(String fileName) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://friday1-3.onrender.com/files/" + fileName;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Failed to download file", Toast.LENGTH_SHORT).show()
                        );
                    }
                    return;
                }
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadsDir.exists()) downloadsDir.mkdirs();
                File outFile = new File(downloadsDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(response.body().bytes());
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "Error saving file: " + e.getMessage(), Toast.LENGTH_LONG).show()
                        );
                    }
                    return;
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Downloaded to: " + outFile.getAbsolutePath(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }
} 