import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, FlatList, TouchableOpacity, SafeAreaView, Alert, TextInput, ScrollView, Modal } from 'react-native';
import * as FileSystem from 'expo-file-system';
import TcpSocket from 'react-native-tcp-socket';
import { Folder, File as FileIcon, RefreshCw, Image as ImageIcon, Search, Plus, X, Eye, Zap, Trash2, Archive, Copy, Scissors } from 'lucide-react-native';
import { useConfig } from '../core/ConfigManager';
import NativeMedia from '../viewers/NativeMedia';
import ImageViewer from '../viewers/ImageViewer';
import DocumentViewer from '../viewers/DocumentViewer';

export default function Dashboard() {
    const { config, saveConfig } = useConfig();
    const [tabs, setTabs] = useState([{ id: 1, path: FileSystem.documentDirectory, history: [] }]);
    const [activeTabId, setActiveTabId] = useState(1);
    
    const activeTab = tabs.find(t => t.id === activeTabId);
    const currentPath = activeTab.path;

    const [files, setFiles] = useState([]);
    const [activeMedia, setActiveMedia] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [isSearching, setIsSearching] = useState(false);
    const [eyeStrainMode, setEyeStrainMode] = useState(false);
    const [selectedFile, setSelectedFile] = useState(null);

    // Create Styles dynamically based on the Config Context
    const styles = StyleSheet.create({
        container: { flex: 1, backgroundColor: config.theme.primaryBg },
        tabsBar: { flexDirection: 'row', backgroundColor: config.theme.secondaryBg, paddingVertical: 8, paddingHorizontal: 16, alignItems: 'center' },
        tab: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.2)', padding: 8, borderRadius: 8, marginRight: 8, borderWidth: 1, borderColor: 'transparent' },
        tabActive: { backgroundColor: config.theme.primaryBg, borderColor: config.theme.accentColor },
        tabText: { color: config.theme.textColor, fontSize: 12, marginRight: 8 },
        header: { padding: 16, backgroundColor: config.theme.secondaryBg, borderBottomWidth: 1, borderColor: 'rgba(255,255,255,0.1)' },
        headerTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
        searchBar: { flexDirection: 'row', alignItems: 'center', backgroundColor: 'rgba(0,0,0,0.2)', borderRadius: 8, paddingHorizontal: 12, height: 40 },
        searchInput: { flex: 1, color: config.theme.textColor, marginLeft: 8 },
        headerText: { color: config.theme.textColor, fontSize: 18, fontWeight: 'bold' },
        fileItem: { flexDirection: 'row', alignItems: 'center', padding: 16, borderBottomWidth: 1, borderColor: 'rgba(255,255,255,0.05)', backgroundColor: config.theme.primaryBg },
        fileName: { color: config.theme.textColor, fontSize: config.ui.fontSize, marginLeft: 16, flex: 1 },
        eyeStrainOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(255, 165, 0, 0.15)', pointerEvents: 'none', zIndex: 9999 },
        actionMenu: { position: 'absolute', bottom: 0, left: 0, right: 0, backgroundColor: config.theme.secondaryBg, padding: 20, borderTopLeftRadius: 20, borderTopRightRadius: 20, flexDirection: 'row', justifyContent: 'space-around' }
    });

    useEffect(() => {
        loadDirectory(currentPath);
    }, [currentPath]);

    const fetchFromDaemon = (action, path, query = "", dest = "") => {
        return new Promise((resolve, reject) => {
            const client = TcpSocket.createConnection({ port: 50505, host: '127.0.0.1' }, () => {
                client.write(JSON.stringify({ action, path, query, dest }));
            });

            let dataBuffer = '';
            client.on('data', (data) => { dataBuffer += data.toString(); });
            client.on('close', () => {
                try {
                    const parsed = JSON.parse(dataBuffer);
                    if (parsed.success) resolve(parsed);
                    else reject(new Error(parsed.error));
                } catch (e) { reject(e); }
            });
            client.on('error', reject);
        });
    };

    const loadDirectory = async (path) => {
        try {
            const dirContent = await FileSystem.readDirectoryAsync(path);
            const detailedFiles = await Promise.all(
                dirContent.map(async (item) => {
                    const fullPath = path + (path.endsWith('/') ? '' : '/') + item;
                    const info = await FileSystem.getInfoAsync(fullPath);
                    return { name: item, uri: fullPath, ...info };
                })
            );
            detailedFiles.sort((a, b) => {
                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;
                return a.name.localeCompare(b.name);
            });
            setFiles(detailedFiles);
        } catch (error) {
            console.log("Standard API Failed, attempting Elevated Daemon...");
            try {
                const res = await fetchFromDaemon('list_dir', path);
                const daemonFiles = res.files;
                daemonFiles.sort((a, b) => {
                    if (a.isDirectory && !b.isDirectory) return -1;
                    if (!a.isDirectory && b.isDirectory) return 1;
                    return a.name.localeCompare(b.name);
                });
                setFiles(daemonFiles);
            } catch (daemonError) {
                Alert.alert("Permission Denied", "Cannot access directory natively, and Daemon is unreachable or failed.");
            }
        }
    };

    const performSearch = async () => {
        if (!searchQuery.trim()) {
            loadDirectory(currentPath);
            setIsSearching(false);
            return;
        }
        setIsSearching(true);
        try {
            const res = await fetchFromDaemon('search_files', currentPath, searchQuery);
            setFiles(res.files);
        } catch (e) {
            Alert.alert("Search Error", "Could not complete native search.");
            setIsSearching(false);
        }
    };

    const handleFilePress = (file) => {
        if (file.isDirectory) {
            updateTabPath(activeTabId, file.uri + (file.uri.endsWith('/') ? '' : '/'));
        } else {
            const ext = file.name.split('.').pop().toLowerCase();
            const videos = ['mp4', 'mkv', 'avi', 'webm'];
            const audio = ['mp3', 'wav', 'ogg', 'flac'];
            const images = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
            const documents = ['txt', 'md', 'js', 'json', 'py', 'java', 'html', 'css', 'c', 'cpp', 'rs'];
            
            if (videos.includes(ext)) setActiveMedia({ uri: file.uri, type: 'video', name: file.name });
            else if (audio.includes(ext)) setActiveMedia({ uri: file.uri, type: 'audio', name: file.name });
            else if (images.includes(ext)) setActiveMedia({ uri: file.uri, type: 'image', name: file.name });
            else if (documents.includes(ext)) setActiveMedia({ uri: file.uri, type: 'document', name: file.name });
            else Alert.alert("Open File", `Opening ${file.name} (Requires External Intent)`);
        }
    };

    // Tab Management
    const addTab = () => {
        const newId = Date.now();
        setTabs([...tabs, { id: newId, path: FileSystem.documentDirectory, history: [] }]);
        setActiveTabId(newId);
    };

    const closeTab = (id) => {
        if (tabs.length === 1) return; // Don't close last tab
        const newTabs = tabs.filter(t => t.id !== id);
        setTabs(newTabs);
        if (activeTabId === id) setActiveTabId(newTabs[0].id);
    };

    const updateTabPath = (id, newPath) => {
        setTabs(tabs.map(t => t.id === id ? { ...t, path: newPath } : t));
    };

    // Advanced Actions
    const handleAction = async (actionType) => {
        if (!selectedFile && actionType !== 'enable_automation' && actionType !== 'find_duplicates') return;
        
        try {
            if (actionType === 'delete') {
                await fetchFromDaemon('delete_file', selectedFile.uri);
                Alert.alert("Success", "Moved to Trash (Deleted)");
            } else if (actionType === 'archive') {
                await fetchFromDaemon('archive', selectedFile.uri);
                Alert.alert("Success", "Archived successfully.");
            } else if (actionType === 'enable_automation') {
                const res = await fetchFromDaemon('enable_automation', currentPath);
                Alert.alert("Automation Engine", res.message);
            } else if (actionType === 'find_duplicates') {
                const res = await fetchFromDaemon('find_duplicates', currentPath);
                Alert.alert("Duplicate Scan", res.duplicates || "No exact duplicates found.");
            }
            setSelectedFile(null);
            loadDirectory(currentPath);
        } catch (e) {
            Alert.alert("Action Failed", e.message);
        }
    };

    return (
        <SafeAreaView style={styles.container}>
            {/* Seamless Tabs */}
            <ScrollView horizontal style={styles.tabsBar} showsHorizontalScrollIndicator={false}>
                {tabs.map(tab => (
                    <TouchableOpacity key={tab.id} style={[styles.tab, activeTabId === tab.id && styles.tabActive]} onPress={() => setActiveTabId(tab.id)}>
                        <Text style={styles.tabText}>{tab.path.split('/').pop() || 'Root'}</Text>
                        <TouchableOpacity onPress={() => closeTab(tab.id)}>
                            <X color={config.theme.textColor} size={14} />
                        </TouchableOpacity>
                    </TouchableOpacity>
                ))}
                <TouchableOpacity onPress={addTab} style={{ padding: 8 }}>
                    <Plus color={config.theme.textColor} size={20} />
                </TouchableOpacity>
            </ScrollView>

            <View style={styles.header}>
                <View style={styles.headerTop}>
                    <Text style={styles.headerText}>
                        {isSearching ? 'Search Results' : `PFS - ${currentPath.split('/').pop() || 'Root'}`}
                    </Text>
                    <View style={{ flexDirection: 'row', gap: 16 }}>
                        <TouchableOpacity onPress={() => handleAction('enable_automation')}>
                            <Zap color="#f59e0b" size={24} />
                        </TouchableOpacity>
                        <TouchableOpacity onPress={() => setEyeStrainMode(!eyeStrainMode)}>
                            <Eye color={eyeStrainMode ? "#f59e0b" : config.theme.textColor} size={24} />
                        </TouchableOpacity>
                    </View>
                </View>
                <View style={styles.searchBar}>
                    <Search color={config.theme.textColor} size={20} />
                    <TextInput 
                        style={styles.searchInput}
                        placeholder="Universal Fast Search..."
                        placeholderTextColor="rgba(255,255,255,0.4)"
                        value={searchQuery}
                        onChangeText={setSearchQuery}
                        onSubmitEditing={performSearch}
                        returnKeyType="search"
                    />
                </View>
            </View>

            <FlatList
                data={files}
                keyExtractor={(item, index) => item.uri + index}
                renderItem={({ item }) => (
                    <TouchableOpacity 
                        style={styles.fileItem} 
                        onPress={() => handleFilePress(item)}
                        onLongPress={() => setSelectedFile(item)}
                    >
                        {item.isDirectory ? 
                            <Folder color="#fbbf24" size={config.ui.iconSize / 1.5} /> : 
                            <FileIcon color={config.theme.accentColor} size={config.ui.iconSize / 1.5} />
                        }
                        <Text style={styles.fileName} numberOfLines={1}>{item.name}</Text>
                    </TouchableOpacity>
                )}
            />

            {/* Action Menu (Shows on Long Press) */}
            {selectedFile && (
                <View style={styles.actionMenu}>
                    <TouchableOpacity onPress={() => handleAction('archive')}><Archive color={config.theme.textColor} size={24} /></TouchableOpacity>
                    <TouchableOpacity onPress={() => handleAction('delete')}><Trash2 color="#ef4444" size={24} /></TouchableOpacity>
                    <TouchableOpacity onPress={() => setSelectedFile(null)}><X color={config.theme.textColor} size={24} /></TouchableOpacity>
                </View>
            )}

            {/* Viewers */}
            {activeMedia && (activeMedia.type === 'video' || activeMedia.type === 'audio') && (
                <NativeMedia uri={activeMedia.uri} type={activeMedia.type} onClose={() => setActiveMedia(null)} />
            )}
            {activeMedia && activeMedia.type === 'image' && (
                <ImageViewer uri={activeMedia.uri} onClose={() => setActiveMedia(null)} />
            )}
            {activeMedia && activeMedia.type === 'document' && (
                <DocumentViewer uri={activeMedia.uri} name={activeMedia.name} onClose={() => setActiveMedia(null)} />
            )}

            {/* Eye Strain Filter */}
            {eyeStrainMode && <View style={styles.eyeStrainOverlay} />}
        </SafeAreaView>
    );
}
