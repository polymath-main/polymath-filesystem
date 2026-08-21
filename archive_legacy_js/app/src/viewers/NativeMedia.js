import React, { useState, useRef, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Text, Dimensions, Animated, PanResponder } from 'react-native';
import { Video, Audio } from 'expo-av';
import * as ScreenOrientation from 'expo-screen-orientation';
import { X, Play, Pause, Maximize, Minimize, Move, Subtitles } from 'lucide-react-native';
import { useConfig } from '../core/ConfigManager';

export default function NativeMedia({ uri, type, onClose }) {
    const { config } = useConfig();
    const videoRef = useRef(null);
    const [status, setStatus] = useState({});
    
    // UI States
    const [isFloating, setIsFloating] = useState(false);
    const [isFullscreen, setIsFullscreen] = useState(false);
    
    // Animation Values
    const pan = useRef(new Animated.ValueXY()).current;
    const scale = useRef(new Animated.Value(1)).current; // 1 = normal, 0.4 = floating
    
    const isAudio = type === 'audio';

    useEffect(() => {
        // Configure Background Audio Playback
        Audio.setAudioModeAsync({
            allowsRecordingIOS: false,
            staysActiveInBackground: true,
            playsInSilentModeIOS: true,
            shouldDuckAndroid: true,
            playThroughEarpieceAndroid: false,
        });

        return () => {
            ScreenOrientation.unlockAsync();
        };
    }, []);

    // Drag Logic for Pop-up mode
    const panResponder = useRef(
        PanResponder.create({
            onMoveShouldSetPanResponder: () => isFloating,
            onPanResponderGrant: () => {
                pan.setOffset({ x: pan.x._value, y: pan.y._value });
            },
            onPanResponderMove: Animated.event(
                [null, { dx: pan.x, dy: pan.y }],
                { useNativeDriver: false }
            ),
            onPanResponderRelease: () => {
                pan.flattenOffset();
            }
        })
    ).current;

    const toggleFloating = () => {
        const toValue = isFloating ? 1 : 0.4;
        Animated.spring(scale, {
            toValue,
            useNativeDriver: false,
        }).start();
        
        if (isFloating) {
            Animated.spring(pan, { toValue: { x: 0, y: 0 }, useNativeDriver: false }).start();
        }
        
        setIsFloating(!isFloating);
        setIsFullscreen(false);
        ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
    };

    const toggleFullscreen = async () => {
        if (!isFullscreen) {
            await ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.LANDSCAPE);
            setIsFullscreen(true);
            setIsFloating(false);
        } else {
            await ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
            setIsFullscreen(false);
        }
    };

    const togglePlayPause = () => {
        if (status.isPlaying) {
            videoRef.current?.pauseAsync();
        } else {
            videoRef.current?.playAsync();
        }
    };

    const handleClose = async () => {
        await videoRef.current?.stopAsync();
        await ScreenOrientation.unlockAsync();
        onClose();
    };

    const styles = StyleSheet.create({
        container: {
            position: isFloating ? 'absolute' : 'absolute',
            top: 0, bottom: 0, left: 0, right: 0,
            zIndex: 9999,
            pointerEvents: 'box-none',
            justifyContent: 'center',
            alignItems: 'center',
        },
        animatedWrapper: {
            width: Dimensions.get('window').width,
            height: Dimensions.get('window').height,
            justifyContent: 'center',
            alignItems: 'center',
            backgroundColor: isFloating ? 'transparent' : 'rgba(0,0,0,0.95)',
        },
        header: {
            position: 'absolute',
            top: isFullscreen ? 20 : 40,
            right: 20,
            zIndex: 1001,
            flexDirection: 'row',
            gap: 16
        },
        mediaPlayer: {
            width: isFullscreen ? '100%' : '90%',
            height: isFullscreen ? '100%' : 300,
            backgroundColor: '#000',
            borderRadius: isFullscreen ? 0 : 16,
            elevation: isFloating ? 10 : 0,
        },
        controlOverlay: {
            position: 'absolute',
            bottom: isFloating ? -50 : 20,
            flexDirection: 'row',
            gap: 20,
            backgroundColor: 'rgba(0,0,0,0.7)',
            padding: 10,
            borderRadius: 50,
            zIndex: 1002,
        }
    });

    return (
        <View style={styles.container} pointerEvents="box-none">
            <Animated.View 
                style={[
                    styles.animatedWrapper,
                    {
                        transform: [
                            { translateX: pan.x },
                            { translateY: pan.y },
                            { scale: scale }
                        ]
                    }
                ]}
                {...panResponder.panHandlers}
            >
                {!isFloating && (
                    <View style={styles.header}>
                        {!isAudio && (
                            <>
                                <TouchableOpacity onPress={() => {/* Subtitle Logic Placeholder */}}>
                                    <Subtitles color="#fff" size={28} />
                                </TouchableOpacity>
                                <TouchableOpacity onPress={toggleFloating}>
                                    <Move color="#fff" size={28} />
                                </TouchableOpacity>
                                <TouchableOpacity onPress={toggleFullscreen}>
                                    {isFullscreen ? <Minimize color="#fff" size={28} /> : <Maximize color="#fff" size={28} />}
                                </TouchableOpacity>
                            </>
                        )}
                        <TouchableOpacity onPress={handleClose}>
                            <X color="#fff" size={32} />
                        </TouchableOpacity>
                    </View>
                )}

                <Video
                    ref={videoRef}
                    style={styles.mediaPlayer}
                    source={{ uri }}
                    useNativeControls={!isFloating}
                    resizeMode={isFullscreen ? "cover" : "contain"}
                    isLooping
                    onPlaybackStatusUpdate={status => setStatus(() => status)}
                />

                {isFloating && (
                    <View style={styles.controlOverlay}>
                        <TouchableOpacity onPress={toggleFloating}>
                            <Maximize color="#fff" size={24} />
                        </TouchableOpacity>
                        <TouchableOpacity onPress={togglePlayPause}>
                            {status.isPlaying ? <Pause color="#fff" size={24} /> : <Play color="#fff" size={24} />}
                        </TouchableOpacity>
                        <TouchableOpacity onPress={handleClose}>
                            <X color="#ef4444" size={24} />
                        </TouchableOpacity>
                    </View>
                )}
            </Animated.View>
        </View>
    );
}
