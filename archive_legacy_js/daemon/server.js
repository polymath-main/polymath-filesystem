const express = require('express');
const cors = require('cors');
const path = require('path');
const http = require('http');
const { WebSocketServer } = require('ws');
const fsStandard = require('./core/fs_standard');

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, '../frontend')));

// API Routes
app.get('/api/files', async (req, res) => {
    try {
        const dirPath = req.query.path || '/data/data/com.termux/files/home';
        const files = await fsStandard.listDir(dirPath);
        res.json({ success: true, path: dirPath, files });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// WebSocket for real-time events (e.g. file watch, progress)
wss.on('connection', (ws) => {
    console.log('Client connected to Polymath Nexus WebSocket');
    ws.send(JSON.stringify({ type: 'WELCOME', message: 'Connected to Polymath Files System Engine' }));
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
    console.log(`[Polymath Engine] Running on http://localhost:${PORT}`);
});
