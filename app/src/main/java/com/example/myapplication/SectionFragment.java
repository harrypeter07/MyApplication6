package com.example.myapplication;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.ImageButton;
import com.google.android.material.button.MaterialButton;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.widget.PopupMenu;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.text.ParseException;
import android.widget.Button;
import android.widget.FrameLayout;

public class SectionFragment extends Fragment {
    private static final String ARG_TYPE = "type";
    private static final String ARG_FILTER = "filter";
    private String type;
    private String filter;
    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    public static class FileItem {
        String filename;
        String uploadedAt;
        Date uploadedDate;
        FileItem(String filename, String uploadedAt) {
            this.filename = filename;
            this.uploadedAt = uploadedAt;
            try {
                this.uploadedDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(uploadedAt);
            } catch (Exception e) {
                this.uploadedDate = new Date(0);
            }
        }
    }
    private static List<FileItem> allFiles = new ArrayList<>();
    private static boolean filesLoaded = false;
    private static final Object lock = new Object();
    private String sortMode = "recents";
    private TextInputEditText searchEditText;
    private TextInputLayout searchInputLayout;
    private TextView emptyStateText;
    private String currentFilter = "";
    private MaterialButton refreshButton;
    private MaterialButton sortButton;
    private MaterialButton deleteButton;
    private static final long TWENTY_MB = 20 * 1024 * 1024;
    private List<String> autoDownloaded = new ArrayList<>();
    private final android.os.Handler autoRefreshHandler = new android.os.Handler();
    private boolean isFetching = false;
    private boolean isDialogOpen = false;
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (isResumed() && "images".equals(type)) {
                if (!isFetching && !isDialogOpen) {
                    fetchFilesFromServer(() -> checkForNewImagesAndOpen());
                }
                autoRefreshHandler.postDelayed(this, 200); // 200 milliseconds
            }
        }
    };
    private List<String> lastSeenImageFiles = new ArrayList<>();
    private String lastOpenedImage = null;
    private boolean isImageOpened = false;
    private String latestImage = null;
    private android.app.Dialog currentImageDialog = null;
    private Button permissionButton;
    private FrameLayout rootLayout;

    public static SectionFragment newInstance(String type, String filter) {
        SectionFragment fragment = new SectionFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_FILTER, filter);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_section, container, false);
        recyclerView = view.findViewById(R.id.sectionRecyclerView);
        searchEditText = view.findViewById(R.id.searchEditText);
        searchInputLayout = view.findViewById(R.id.searchInputLayout);
        emptyStateText = view.findViewById(R.id.emptyStateText);
        refreshButton = view.findViewById(R.id.refreshButton);
        sortButton = view.findViewById(R.id.sortButton);
        deleteButton = view.findViewById(R.id.deleteButton);
        if (view instanceof FrameLayout) {
            rootLayout = (FrameLayout) view;
        }
        if (getArguments() != null) {
            type = getArguments().getString(ARG_TYPE, "others");
            filter = getArguments().getString(ARG_FILTER, "");
        }
        searchEditText.setText(currentFilter);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentFilter = s.toString();
                updateFilesUI();
                // Show/hide clear button
                searchInputLayout.setEndIconVisible(s.length() > 0);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        searchInputLayout.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        refreshButton.setOnClickListener(v -> {
            fetchFilesFromServer(this::updateFilesUI);
        });
        sortButton.setOnClickListener(v -> showSortMenu());
        deleteButton.setOnClickListener(v -> {
            if (fileAdapter != null) {
                fileAdapter.deleteSelectedFiles();
            }
        });
        if (!filesLoaded) {
            fetchFilesFromServer(() -> {
                updateFilesUI();
            });
        } else {
            updateFilesUI();
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            fetchFilesFromServer(() -> {
                updateFilesUI();
                checkAndOpenLatestImage();
            });
            if ("images".equals(type)) {
                autoRefreshHandler.postDelayed(autoRefreshRunnable, 200);
            }
        } else {
            showPermissionButton();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if ("images".equals(type)) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }
    }

    private void fetchFilesFromServer(Runnable onDone) {
        if (isFetching) return;
        isFetching = true;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://friday1-3.onrender.com/api/files")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                isFetching = false;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateFilesUI());
                }
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                isFetching = false;
                if (!response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateFilesUI());
                    }
                    return;
                }
                String json = response.body().string();
                List<FileItem> fileItems = new ArrayList<>();
                try {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        fileItems.add(new FileItem(obj.getString("filename"), obj.getString("uploaded_at")));
                    }
                } catch (Exception ignored) {}
                synchronized (lock) {
                    allFiles = fileItems;
                    filesLoaded = true;
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        onDone.run();
                        // Auto-download files < 20MB
                        autoDownloaded.clear();
                        for (FileItem file : fileItems) {
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
        client.newCall(headRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { /* ignore */ }
            @Override
            public void onResponse(Call call, Response response) {
                long size = 0;
                try {
                    String len = response.header("Content-Length");
                    if (len != null) size = Long.parseLong(len);
                } catch (Exception ignored) {}
                if (size > 0 && size < TWENTY_MB && !autoDownloaded.contains(fileName)) {
                    autoDownloaded.add(fileName);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            downloadFileSilently(fileName);
                            if (isImageFile(fileName)) {
                                showImageViewerWithAnimation(fileName);
                            }
                        });
                    }
                }
            }
        });
    }

    private void updateFilesUI() {
        List<FileItem> files = getFilesForSection(type, currentFilter);
        if (type.equals("images")) {
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        fileAdapter = new FileAdapter(getContext(), files, new FileAdapter.OnFileClickListener() {
            @Override
            public void onFileClick(String fileName) {
                openFileOrImage(fileName);
            }
            @Override
            public void onFileLongClick(String fileName, View anchor) {
                // TODO: handle long click
            }
        }, new FileAdapter.OnDownloadClickListener() {
            @Override
            public void onDownloadClick(String fileName) {
                downloadFileFromServer(fileName);
            }
        });
        recyclerView.setAdapter(fileAdapter);
        // Empty state
        if (files.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private List<FileItem> getFilesForSection(String type, String filter) {
        List<FileItem> filtered = new ArrayList<>();
        synchronized (lock) {
            for (FileItem f : allFiles) {
                if (matchesType(f.filename, type) && (filter == null || filter.isEmpty() || f.filename.toLowerCase().contains(filter.toLowerCase()))) {
                    filtered.add(f);
                }
            }
        }
        sortFiles(filtered);
        return filtered;
    }

    private boolean matchesType(String fileName, String type) {
        String lower = fileName.toLowerCase();
        switch (type) {
            case "images":
                return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
            case "zips":
                return lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z");
            case "files":
                return lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".xls") || lower.endsWith(".xlsx");
            case "others":
                return !(matchesType(fileName, "images") || matchesType(fileName, "zips") || matchesType(fileName, "files"));
        }
        return false;
    }

    private void sortFiles(List<FileItem> files) {
        if (sortMode.equals("name")) {
            Collections.sort(files, (a, b) -> a.filename.compareToIgnoreCase(b.filename));
        } else if (sortMode.equals("type")) {
            Collections.sort(files, (a, b) -> {
                String typeA = getFileType(a.filename);
                String typeB = getFileType(b.filename);
                int typeComparison = typeA.compareTo(typeB);
                if (typeComparison != 0) {
                    return typeComparison;
                }
                // Secondary sort by name within type
                return a.filename.compareToIgnoreCase(b.filename);
            });
        } else if (sortMode.equals("dateOldest")) {
             Collections.sort(files, (a, b) -> a.uploadedDate.compareTo(b.uploadedDate));
        } else { // default: recents (dateNewest)
            Collections.sort(files, (a, b) -> b.uploadedDate.compareTo(a.uploadedDate));
        }
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
                    getActivity().runOnUiThread(() -> openFileWithApp(outFile, fileName));
                }
            }
        });
    }

    private void openFileOrImage(String fileName) {
        if (isImageFile(fileName)) {
            showImageViewer(fileName);
        } else {
            File file = getPublicDownloadsFile(fileName);
            if (!file.exists()) {
                downloadFileFromServerAndOpen(fileName);
            } else {
                openFileWithApp(file, fileName);
            }
        }
    }

    private void downloadFileFromServerAndOpen(String fileName) {
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
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
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
                    getActivity().runOnUiThread(() -> openFileWithApp(outFile, fileName));
                }
            }
        });
    }

    private void openFileWithApp(File file, String fileName) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.net.Uri fileUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
            intent.setDataAndType(fileUri, getMimeType(fileName));
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "No app found to open this file type.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String fileName) {
        String type = null;
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null) {
            type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return type != null ? type : "application/octet-stream";
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(getContext(), sortButton);
        // Define menu items and IDs
        final int SORT_RECENT_ID = 0;
        final int SORT_NAME_ID = 1;
        final int SORT_TYPE_ID = 2;
        final int SORT_OLDEST_ID = 3;

        // Add menu items with checkmarks
        popup.getMenu().add(0, SORT_RECENT_ID, 0, sortMode.equals("recents") ? "✓ Sort by Date (Newest)" : "Sort by Date (Newest)");
        popup.getMenu().add(0, SORT_NAME_ID, 1, sortMode.equals("name") ? "✓ Sort by Name" : "Sort by Name");
        popup.getMenu().add(0, SORT_TYPE_ID, 2, sortMode.equals("type") ? "✓ Sort by Type" : "Sort by Type");
        popup.getMenu().add(0, SORT_OLDEST_ID, 3, sortMode.equals("dateOldest") ? "✓ Sort by Date (Oldest)" : "Sort by Date (Oldest)");
        // TODO: Add Sort by Size if API supports it

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == SORT_RECENT_ID) {
                sortMode = "recents";
            } else if (id == SORT_NAME_ID) {
                sortMode = "name";
            } else if (id == SORT_TYPE_ID) {
                sortMode = "type";
            } else if (id == SORT_OLDEST_ID) {
                sortMode = "dateOldest";
            }
            updateFilesUI(); // Refresh the list with the new sort order
            return true;
        });
        popup.show();
    }

    private String getFileType(String fileName) {
        if (isImageFile(fileName)) return "images";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".txt")) return "text";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "zip";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "doc";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "excel";
        return "other";
    }

    private void showImageViewer(String fileName) {
        File imageFile = getPublicDownloadsFile(fileName);
        if (!imageFile.exists()) {
            Toast.makeText(getContext(), "Image not downloaded yet. Please tap again after a moment.", Toast.LENGTH_SHORT).show();
            return;
        }
        android.app.Dialog dialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.widget.ImageView imageView = new android.widget.ImageView(getContext());
        imageView.setImageBitmap(android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
        imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        dialog.setContentView(imageView);
        imageView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private File getPublicDownloadsFile(String fileName) {
        File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        return new File(downloadsDir, fileName);
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private void downloadFileSilently(String fileName) {
        OkHttpClient client = new OkHttpClient();
        String url = "https://friday1-3.onrender.com/files/" + fileName;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { /* ignore */ }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) return;
                File outFile = getPublicDownloadsFile(fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                    fos.write(response.body().bytes());
                } catch (Exception ignored) {}
            }
        });
    }

    private void showImageViewerWithAnimation(String fileName) {
        if (isDialogOpen) {
            // If a dialog is already open, dismiss it first
            if (currentImageDialog != null && currentImageDialog.isShowing()) {
                currentImageDialog.dismiss();
            }
        }
        
        isDialogOpen = true;
        isImageOpened = true;
        File imageFile = getPublicDownloadsFile(fileName);
        if (!imageFile.exists()) {
            Toast.makeText(getContext(), "Image not downloaded yet. Please tap again after a moment.", Toast.LENGTH_SHORT).show();
            isDialogOpen = false;
            isImageOpened = false;
            return;
        }

        currentImageDialog = new android.app.Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        
        // Create a FrameLayout to hold both the image and close button
        FrameLayout container = new FrameLayout(getContext());
        
        // Create and setup the image view
        android.widget.ImageView imageView = new android.widget.ImageView(getContext());
        imageView.setImageBitmap(android.graphics.BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
        imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        imageView.setAlpha(0f);
        
        // Create and setup the close button
        ImageButton closeButton = new ImageButton(getContext());
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setBackgroundResource(android.R.color.transparent);
        closeButton.setPadding(32, 32, 32, 32);
        
        // Add click listeners
        closeButton.setOnClickListener(v -> {
            currentImageDialog.dismiss();
            isDialogOpen = false;
            isImageOpened = false;
        });
        
        imageView.setOnClickListener(v -> {
            currentImageDialog.dismiss();
            isDialogOpen = false;
            isImageOpened = false;
        });
        
        // Add views to container
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.addView(imageView, imageParams);
        
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        buttonParams.setMargins(0, 32, 32, 0);
        container.addView(closeButton, buttonParams);
        
        currentImageDialog.setContentView(container);
        imageView.animate().alpha(1f).setDuration(400).start();
        currentImageDialog.setOnDismissListener(dialog -> {
            isDialogOpen = false;
            isImageOpened = false;
        });
        currentImageDialog.show();
    }

    private void checkForNewImagesAndOpen() {
        if (!isResumed()) return;
        
        List<FileItem> imageFiles = new ArrayList<>();
        synchronized (lock) {
            for (FileItem file : allFiles) {
                if (isImageFile(file.filename)) {
                    imageFiles.add(file);
                }
            }
        }
        
        if (imageFiles.isEmpty()) return;
        
        // Sort by date to find the newest image
        Collections.sort(imageFiles, (a, b) -> b.uploadedDate.compareTo(a.uploadedDate));
        FileItem newestImage = imageFiles.get(0);
        
        // Only open if it's a new image and no image is currently opened
        if ((lastOpenedImage == null || !lastOpenedImage.equals(newestImage.filename)) && !isImageOpened) {
            File imageFile = getPublicDownloadsFile(newestImage.filename);
            if (imageFile.exists()) {
                showImageViewerWithAnimation(newestImage.filename);
                lastOpenedImage = newestImage.filename;
            }
        }
    }

    private boolean safeEquals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }

    private java.util.Date parseDate(String dateStr) {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(dateStr);
        } catch (Exception e) {
            return new java.util.Date(0);
        }
    }

    private void checkAndOpenLatestImage() {
        if (latestImage != null && !safeEquals(lastOpenedImage, latestImage)) {
            File imageFile = getPublicDownloadsFile(latestImage);
            if (imageFile.exists()) {
                showImageViewerWithAnimation(latestImage);
                lastOpenedImage = latestImage;
            }
        }
    }

    private void deleteFile(String fileName) {
        File file = getPublicDownloadsFile(fileName);
        if (file.exists()) {
            if (file.delete()) {
                Toast.makeText(getContext(), "File deleted: " + fileName, Toast.LENGTH_SHORT).show();
                updateFilesUI();
            } else {
                Toast.makeText(getContext(), "Failed to delete file: " + fileName, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            return android.os.Environment.isExternalStorageManager();
        } else {
            // Android 10 and below
            return androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
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
            if (rootLayout != null) {
                rootLayout.addView(permissionButton, params);
            }
        }
        if (getView() != null) {
            View recyclerView = getView().findViewById(R.id.sectionRecyclerView);
            View progressBar = getView().findViewById(R.id.progressBar);
            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
        }
        permissionButton.setVisibility(View.VISIBLE);
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            requestAllFilesAccess();
        } else {
            requestLegacyStoragePermissions();
        }
    }

    private void requestAllFilesAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                new android.app.AlertDialog.Builder(getContext())
                        .setTitle("Storage Permission Required")
                        .setMessage("This app needs access to manage all files on your device to function properly. Please grant 'All files access' permission in the next screen.")
                        .setPositiveButton("Continue", (dialog, which) -> {
                            android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(android.net.Uri.parse("package:" + requireContext().getPackageName()));
                            startActivityForResult(intent, 124);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            Toast.makeText(getContext(), "Storage permission is required for app functionality", Toast.LENGTH_LONG).show();
                        })
                        .show();
            } catch (Exception e) {
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, 124);
            }
        }
    }

    private void requestLegacyStoragePermissions() {
        if (shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs storage permission to read and write files on your device.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        requestPermissions(new String[]{
                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 123);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Toast.makeText(getContext(), "Storage permission is required for app functionality", Toast.LENGTH_LONG).show();
                    })
                    .show();
        } else {
            requestPermissions(new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 123);
        }
    }
} 