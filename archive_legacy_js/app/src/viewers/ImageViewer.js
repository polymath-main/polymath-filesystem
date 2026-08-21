import React from 'react';
import { View, StyleSheet, TouchableOpacity, Dimensions, Image, ScrollView } from 'react-native';
import { X } from 'lucide-react-native';

export default function ImageViewer({ uri, onClose }) {
    const { width, height } = Dimensions.get('window');

    const styles = StyleSheet.create({
        container: {
            ...StyleSheet.absoluteFillObject,
            backgroundColor: 'rgba(0,0,0,0.95)',
            zIndex: 1000,
        },
        header: {
            position: 'absolute',
            top: 40,
            right: 20,
            zIndex: 1001,
        },
        imageContainer: {
            flex: 1,
            justifyContent: 'center',
            alignItems: 'center',
        },
        image: {
            width: width,
            height: height,
            resizeMode: 'contain',
        }
    });

    return (
        <View style={styles.container}>
            <View style={styles.header}>
                <TouchableOpacity onPress={onClose}>
                    <X color="#fff" size={32} />
                </TouchableOpacity>
            </View>
            <ScrollView 
                contentContainerStyle={styles.imageContainer}
                maximumZoomScale={4}
                minimumZoomScale={1}
                showsHorizontalScrollIndicator={false}
                showsVerticalScrollIndicator={false}
                centerContent
            >
                <Image source={{ uri }} style={styles.image} />
            </ScrollView>
        </View>
    );
}
