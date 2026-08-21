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
import com.polymath.fs.models.TabState;
import java.util.UUID;
import android.content.Context;
import android.database.Cursor;
import com.polymath.fs.core.SearchDatabaseHelper;
import com.polymath.fs.core.RecentsManager;
import com.polymath.fs.models.SearchResultItem;
import com.polymath.fs.models.RecentFileItem;

public class FileSystemViewModel extends ViewModel {

    private final MutableLiveData<String> currentPath = new MutableLiveData<>();
    private final MutableLiveData<List<FileSystemItem>> fileList = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<List<TabState>> tabs = new MutableLiveData<>(new ArrayList<>());
    private String currentTabId;

    public LiveData<String> getCurrentPath() { return currentPath; }
    public LiveData<List<FileSystemItem>> getFileList() { return fileList; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void navigateTo(String path) {
        if (currentTabId != null) {
            List<TabState> currentTabs = tabs.getValue();
            if (currentTabs != null) {
                for (TabState tab : currentTabs) {
                    if (tab.getTabId().equals(currentTabId)) {
                        if (tab.getPathHistory().isEmpty() || !tab.getPathHistory().peek().equals(path)) {
                            tab.getPathHistory().push(path);
                        }
                        break;
                    }
                }
            }
        }
        navigateToInternal(path);
    }

    private void navigateToInternal(String path) {
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

    public void addNewTab(String path) {
        String tabId = UUID.randomUUID().toString();
        List<TabState> currentTabs = tabs.getValue();
        if (currentTabs == null) {
            currentTabs = new ArrayList<>();
        }
        TabState newTab = new TabState(tabId, "Tab " + (currentTabs.size() + 1));
        newTab.getPathHistory().push(path);
        currentTabs.add(newTab);
        tabs.postValue(currentTabs);
        
        switchTab(tabId);
    }

    public void switchTab(String tabId) {
        this.currentTabId = tabId;
        List<TabState> currentTabs = tabs.getValue();
        if (currentTabs != null) {
            for (TabState tab : currentTabs) {
                if (tab.getTabId().equals(tabId) && !tab.getPathHistory().isEmpty()) {
                    navigateToInternal(tab.getPathHistory().peek());
                    return;
                }
            }
        }
    }

    public void goBack() {
        if (currentTabId == null) return;
        List<TabState> currentTabs = tabs.getValue();
        if (currentTabs != null) {
            for (TabState tab : currentTabs) {
                if (tab.getTabId().equals(currentTabId)) {
                    if (tab.getPathHistory().size() > 1) {
                        tab.getPathHistory().pop();
                        navigateToInternal(tab.getPathHistory().peek());
                    }
                    return;
                }
            }
        }
    }

    public void performSearch(Context context, String query) {
        isLoading.postValue(true);
        currentPath.postValue("Search Results: " + query);
        new Thread(() -> {
            try {
                List<FileSystemItem> items = new ArrayList<>();
                SearchDatabaseHelper dbHelper = new SearchDatabaseHelper(context);
                Cursor cursor = dbHelper.getReadableDatabase().rawQuery("SELECT path, name FROM files_index WHERE name MATCH ? LIMIT 100", new String[]{query + "*"});
                if (cursor.moveToFirst()) {
                    do {
                        String path = cursor.getString(0);
                        String name = cursor.getString(1);
                        items.add(new SearchResultItem(path, name));
                    } while (cursor.moveToNext());
                }
                cursor.close();
                fileList.postValue(items);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }

    public void loadRecents(Context context) {
        isLoading.postValue(true);
        currentPath.postValue("Comfort Zone (Recents)");
        new Thread(() -> {
            try {
                List<FileSystemItem> items = new ArrayList<>();
                RecentsManager recentsManager = new RecentsManager(context);
                List<String> paths = recentsManager.getRecentPaths(50);
                for (String path : paths) {
                    File f = new File(path);
                    if (f.exists()) {
                        items.add(new RecentFileItem(f));
                    }
                }
                fileList.postValue(items);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoading.postValue(false);
            }
        }).start();
    }
}
