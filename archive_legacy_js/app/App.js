import React from 'react';
import { StatusBar } from 'react-native';
import { ConfigProvider, useConfig } from './src/core/ConfigManager';
import Dashboard from './src/ui/Dashboard';

function MainApp() {
    const { config } = useConfig();
    
    return (
        <>
            <StatusBar 
                barStyle={config.theme.mode === 'dark' ? 'light-content' : 'dark-content'} 
                backgroundColor={config.theme.primaryBg} 
            />
            <Dashboard />
        </>
    );
}

export default function App() {
    return (
        <ConfigProvider>
            <MainApp />
        </ConfigProvider>
    );
}
