import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Text, Dimensions, TextInput, ScrollView, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import * as FileSystem from 'expo-file-system';
import Markdown from 'react-native-markdown-display';
import { X, Save, Edit3, Eye } from 'lucide-react-native';
import { useConfig } from '../core/ConfigManager';

export default function DocumentViewer({ uri, name, onClose }) {
    const { config } = useConfig();
    const [content, setContent] = useState('');
    const [isEditing, setIsEditing] = useState(false);
    
    const isMarkdown = name.toLowerCase().endsWith('.md');
    // Start in View mode for markdown, Edit mode for raw code/text by default
    const [viewMode, setViewMode] = useState(isMarkdown ? 'view' : 'edit'); 

    useEffect(() => {
        const loadFile = async () => {
            try {
                const text = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.UTF8 });
                setContent(text);
            } catch (error) {
                Alert.alert("Read Error", "Cannot read file format. It might be a binary file.");
                onClose();
            }
        };
        loadFile();
    }, [uri]);

    const handleSave = async () => {
        try {
            await FileSystem.writeAsStringAsync(uri, content, { encoding: FileSystem.EncodingType.UTF8 });
            Alert.alert("Saved", "Document successfully updated.");
        } catch (error) {
            Alert.alert("Save Error", error.message);
        }
    };

    const toggleMode = () => {
        setViewMode(viewMode === 'view' ? 'edit' : 'view');
    };

    const styles = StyleSheet.create({
        container: {
            ...StyleSheet.absoluteFillObject,
            backgroundColor: config.theme.primaryBg,
            zIndex: 1000,
        },
        header: {
            height: 60,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingHorizontal: 20,
            backgroundColor: config.theme.secondaryBg,
            borderBottomWidth: 1,
            borderColor: 'rgba(255,255,255,0.1)'
        },
        headerTitle: {
            color: config.theme.textColor,
            fontSize: 16,
            fontWeight: 'bold',
            flex: 1,
            marginLeft: 16
        },
        actions: {
            flexDirection: 'row',
            gap: 16
        },
        editorContainer: {
            flex: 1,
        },
        textInput: {
            flex: 1,
            padding: 16,
            color: config.theme.textColor,
            fontFamily: 'monospace', // Built-in monospace font for code editing
            fontSize: config.ui.fontSize,
            textAlignVertical: 'top', // Android
        },
        markdownScroll: {
            flex: 1,
            padding: 16,
        }
    });

    const markdownStyles = {
        body: { color: config.theme.textColor, fontSize: config.ui.fontSize },
        heading1: { color: config.theme.accentColor, marginTop: 10, marginBottom: 10 },
        heading2: { color: config.theme.accentColor, marginTop: 10, marginBottom: 10 },
        code_inline: { backgroundColor: 'rgba(0,0,0,0.3)', color: '#f59e0b', padding: 4, borderRadius: 4 },
        fence: { backgroundColor: 'rgba(0,0,0,0.3)', color: '#f59e0b', padding: 10, borderRadius: 8, fontFamily: 'monospace' },
        link: { color: config.theme.accentColor },
    };

    return (
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
            <View style={styles.header}>
                <TouchableOpacity onPress={onClose}>
                    <X color={config.theme.textColor} size={24} />
                </TouchableOpacity>
                <Text style={styles.headerTitle} numberOfLines={1}>{name}</Text>
                
                <View style={styles.actions}>
                    {isMarkdown && (
                        <TouchableOpacity onPress={toggleMode}>
                            {viewMode === 'view' ? <Edit3 color={config.theme.textColor} size={24} /> : <Eye color={config.theme.textColor} size={24} />}
                        </TouchableOpacity>
                    )}
                    {(viewMode === 'edit' || !isMarkdown) && (
                        <TouchableOpacity onPress={handleSave}>
                            <Save color={config.theme.accentColor} size={24} />
                        </TouchableOpacity>
                    )}
                </View>
            </View>

            <View style={styles.editorContainer}>
                {viewMode === 'view' && isMarkdown ? (
                    <ScrollView style={styles.markdownScroll}>
                        <Markdown style={markdownStyles}>
                            {content}
                        </Markdown>
                    </ScrollView>
                ) : (
                    <TextInput
                        style={styles.textInput}
                        multiline
                        value={content}
                        onChangeText={setContent}
                        placeholder="Type here..."
                        placeholderTextColor="rgba(255,255,255,0.3)"
                        autoCapitalize="none"
                        autoCorrect={false}
                    />
                )}
            </View>
        </KeyboardAvoidingView>
    );
}
