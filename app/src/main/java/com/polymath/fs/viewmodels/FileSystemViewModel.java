package com.polymath.fs.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.polymath.fs.models.FileSystemItem;
import com.polymath.fs.models.LocalFileItem;

public class FileSystemViewModel extends ViewModel {

    private final MutableLiveData<String> currentPath = new MutableLiveData<>();
    private final MutableLiveData<List<FileSystemItem>> fileList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public LiveData<String> getCurrentPath() { return currentPath; }
    public LiveData<List<FileSystemItem>> getFileList() { return fileList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void navigateTo(String path) {
        isLoading.postValue(true);
        currentPath.postValue(path);
        
        new Thread(() -> {
            try {
                File dir = new File(path);
                List<FileSystemItem> items = new ArrayList<>();
                if (dir.exists() && dir.isDirectory()) {
                    File[] rawFiles = dir.listFiles();
                    if (rawFiles != null) {
                        // Sort logic can go here (directories first, then alphabetically)
                        Arrays.sort(rawFiles, (f1, f2) -> {
                            if (f1.isDirectory() && !f2.isDirectory()) return -1;
                            if (!f1.isDirectory() && f2.isDirectory()) return 1;
                            return f1.getName().compareToIgnoreCase(f2.getName());
                        });

                        for (File f : rawFiles) {
                            items.add(new LocalFileItem(f));
                        }
                    }
                }
                fileList.postValue(items);
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }
}
