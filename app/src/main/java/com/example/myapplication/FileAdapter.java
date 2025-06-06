package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import java.io.File;
import com.google.android.material.button.MaterialButton;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
    private List<SectionFragment.FileItem> files;
    private final OnFileClickListener listener;
    private final OnDownloadClickListener downloadListener;

    public interface OnFileClickListener {
        void onFileClick(String fileName);
        void onFileLongClick(String fileName, View anchor);
    }

    public interface OnDownloadClickListener {
        void onDownloadClick(String fileName);
    }

    public FileAdapter(List<SectionFragment.FileItem> files, OnFileClickListener listener, OnDownloadClickListener downloadListener) {
        this.files = files;
        this.listener = listener;
        this.downloadListener = downloadListener;
    }

    public void setFiles(List<SectionFragment.FileItem> files) {
        this.files = files;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        SectionFragment.FileItem fileItem = files.get(position);
        String fileName = fileItem.filename;
        holder.textView.setText(fileName);
        holder.dateText.setText(fileItem.uploadedAt);
        if (isImageFile(fileName)) {
            File imageFile = getPublicDownloadsFile(fileName);
            if (imageFile.exists()) {
                Glide.with(holder.iconView.getContext())
                    .load(imageFile)
                    .apply(new RequestOptions().placeholder(R.drawable.ic_image).centerCrop())
                    .into(holder.iconView);
            } else {
                String url = "https://friday1-3.onrender.com/files/" + fileName;
                Glide.with(holder.iconView.getContext())
                    .load(url)
                    .apply(new RequestOptions().placeholder(R.drawable.ic_image).centerCrop())
                    .into(holder.iconView);
            }
        } else {
            holder.iconView.setImageResource(getIconRes(fileName));
            holder.iconView.setColorFilter(ContextCompat.getColor(holder.iconView.getContext(), R.color.colorPrimary));
        }
        holder.itemView.setOnClickListener(v -> listener.onFileClick(fileName));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFileLongClick(fileName, holder.itemView);
            return true;
        });
        holder.downloadButton.setOnClickListener(v -> downloadListener.onDownloadClick(fileName));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        TextView dateText;
        ImageView iconView;
        MaterialButton downloadButton;
        FileViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text1);
            dateText = itemView.findViewById(R.id.dateText);
            iconView = itemView.findViewById(R.id.icon);
            downloadButton = itemView.findViewById(R.id.downloadButton);
        }
    }

    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp");
    }

    private File getPublicDownloadsFile(String fileName) {
        File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        return new File(downloadsDir, fileName);
    }

    private int getIconRes(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
            return R.drawable.ic_image;
        } else if (lower.endsWith(".pdf")) {
            return R.drawable.ic_pdf;
        } else if (lower.endsWith(".txt")) {
            return R.drawable.ic_text;
        } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) {
            return R.drawable.ic_zip;
        } else if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return R.drawable.ic_doc;
        } else if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return R.drawable.ic_excel;
        } else {
            return R.drawable.ic_file;
        }
    }
} 