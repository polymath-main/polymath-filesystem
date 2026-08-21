import * as FileSystem from 'expo-file-system';
import { useState, useEffect, createContext, useContext } from 'react';

const CONFIG_PATH = FileSystem.documentDirectory + 'pfs_config.json';

const DEFAULT_CONFIG = {
    theme: {
        mode: 'dark', // 'dark' | 'light' | 'amoled'
        accentColor: '#3b82f6',
        primaryBg: '#0f172a',
        secondaryBg: '#1e293b',
        textColor: '#f8fafc'
    },
    ui: {
        viewMode: 'grid', // 'grid' | 'list'
        iconSize: 48,
        fontSize: 14,
        showHidden: false,
        animationsEnabled: true
    }
};

export const ConfigContext = createContext(null);

export function ConfigProvider({ children }) {
    const [config, setConfig] = useState(DEFAULT_CONFIG);
    const [isLoaded, setIsLoaded] = useState(false);

    // Initialize Config from File
    useEffect(() => {
        const loadConfig = async () => {
            try {
                const info = await FileSystem.getInfoAsync(CONFIG_PATH);
                if (info.exists) {
                    const savedContent = await FileSystem.readAsStringAsync(CONFIG_PATH);
                    setConfig({ ...DEFAULT_CONFIG, ...JSON.parse(savedContent) });
                } else {
                    await saveConfig(DEFAULT_CONFIG);
                }
            } catch (e) {
                console.error("Config Load Error:", e);
            }
            setIsLoaded(true);
        };
        loadConfig();
    }, []);

    // Save and Apply Instantly
    const saveConfig = async (newConfig) => {
        try {
            setConfig(newConfig);
            await FileSystem.writeAsStringAsync(CONFIG_PATH, JSON.stringify(newConfig, null, 2));
        } catch (e) {
            console.error("Config Save Error:", e);
        }
    };

    // Backup & Restore
    const backupConfig = async (targetPath) => {
        await FileSystem.copyAsync({ from: CONFIG_PATH, to: targetPath });
    };

    const restoreConfig = async (sourcePath) => {
        try {
            const restoredContent = await FileSystem.readAsStringAsync(sourcePath);
            const parsed = JSON.parse(restoredContent);
            await saveConfig(parsed); // Instantly triggers re-render across entire app
        } catch (e) {
            console.error("Config Restore Error:", e);
        }
    };

    if (!isLoaded) return null; // Or a loader

    return (
        <ConfigContext.Provider value={{ config, saveConfig, backupConfig, restoreConfig }}>
            {children}
        </ConfigContext.Provider>
    );
}

export const useConfig = () => useContext(ConfigContext);
